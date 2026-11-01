#!/usr/bin/env node
/**
 * Визуальная проверка кнопок: одинаковая высота в ряду, текст не обрезается.
 *
 * Запуск против уже поднятого frontend:
 *   npm --prefix frontend i playwright --no-save
 *   node infra/tools/check-button-layout.mjs http://localhost:5173
 *
 * Скрипт не входит в CI-тесты: нужен реальный браузер и запущенное приложение.
 * Ходит по экранам (вход, устройства и их форма, комнаты, сценарии и их форма,
 * запуски, адаптер) на десктопной и мобильной ширине и печатает таблицу замеров.
 */
import { chromium } from "playwright";

const base = process.argv[2] || "http://localhost:5173";
const widths = [
  { name: "десктоп", width: 1280, height: 800 },
  { name: "мобильный", width: 390, height: 844 },
];

async function measure(page) {
  return page.evaluate(() => {
    const rows = [];
    for (const el of document.querySelectorAll("button")) {
      if (el.getClientRects().length === 0) continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      const label = (el.getAttribute("aria-label") || el.textContent || "")
        .trim()
        .replace(/\s+/g, " ")
        .slice(0, 42);
      rows.push({
        label,
        w: Math.round(r.width),
        h: Math.round(r.height),
        y: Math.round(r.y),
        clippedX: el.scrollWidth > el.clientWidth + 1,
        clippedY: el.scrollHeight > el.clientHeight + 1,
        disabled: !!el.disabled,
      });
    }
    return rows;
  });
}

function judge(rows) {
  const problems = [];
  for (const b of rows) {
    if (b.clippedX || b.clippedY) {
      problems.push(`обрезка: «${b.label}» ${b.w}x${b.h}`);
    }
    const textual = b.label.replace(/[^\p{L}\p{N}]/gu, "").length >= 3;
    if (textual && b.h < 34) {
      problems.push(`низкая текстовая кнопка: «${b.label}» ${b.h}px`);
    }
    if (b.h > 44 && b.h < 60 && textual) {
      // крупные карточки-списки выше 44px — не кнопки панелей, их не трогаем
    }
  }
  const byRow = new Map();
  for (const b of rows) {
    if (b.h > 44) continue; // карточки-списки не сравниваем с кнопками панелей
    const key = Math.round((b.y + b.h / 2) / 12);
    if (!byRow.has(key)) byRow.set(key, []);
    byRow.get(key).push(b);
  }
  for (const group of byRow.values()) {
    if (group.length < 2) continue;
    const heights = group.map((b) => b.h);
    if (Math.max(...heights) - Math.min(...heights) > 2) {
      problems.push(
        `разная высота в ряду: ${group.map((b) => `«${b.label}» ${b.h}px`).join(", ")}`
      );
    }
  }
  return problems;
}

function printScreen(screen, size, rows) {
  console.log(`\n## ${screen} · ${size}`);
  for (const b of rows) {
    console.log(
      `  ${String(b.h).padStart(3)}px × ${String(b.w).padStart(3)}px  ${b.disabled ? "[выкл] " : "       "}«${b.label}»`
    );
  }
}

const browser = await chromium.launch();
let failures = 0;

try {
  for (const size of widths) {
    // Свежий контекст на каждую ширину: экран входа замеряется без сессии.
    const context = await browser.newContext({
      viewport: { width: size.width, height: size.height },
    });
    const page = await context.newPage();

    const check = (screen, rows) => {
      printScreen(screen, size.name, rows);
      for (const p of judge(rows)) {
        console.log(`  !! ${screen}/${size.name}: ${p}`);
        failures += 1;
      }
    };

    await page.goto(base + "/", { waitUntil: "networkidle" });
    check("вход", await measure(page));

    await page.fill("#login", "layout-check");
    await page.fill("#password", "layout-check");
    await page.click("button[type=submit]");
    await page.waitForTimeout(800);

    for (const [name, path] of [
      ["устройства", "/devices"],
      ["сценарии", "/scenarios"],
      ["запуски", "/runs"],
      ["адаптер", "/adapter"],
    ]) {
      await page.goto(base + path, { waitUntil: "networkidle" });
      await page.waitForTimeout(300);
      check(name, await measure(page));
    }

    // Форма устройства (кнопки формы: закрыть, шаблоны, строки сигналов, отмена/создать)
    await page.goto(base + "/devices", { waitUntil: "networkidle" });
    await page.getByRole("button", { name: "Добавить устройство" }).click();
    await page.waitForTimeout(300);
    check("форма устройства", await measure(page));
    await page.getByRole("button", { name: "Отмена" }).click();
    await page.waitForTimeout(200);

    // Форма сценария
    await page.goto(base + "/scenarios", { waitUntil: "networkidle" });
    await page.getByRole("button", { name: "Создать сценарий" }).click();
    await page.waitForTimeout(300);
    check("форма сценария", await measure(page));

    // Комнаты — экран готовых примеров
    await page.evaluate(() => localStorage.setItem("ujin-data-source-v2", "mock"));
    await page.goto(base + "/devices", { waitUntil: "networkidle" });
    await page.waitForTimeout(300);
    check("комнаты", await measure(page));
    await page.evaluate(() => localStorage.setItem("ujin-data-source-v2", "api"));

    await context.close();
  }
} finally {
  await browser.close();
}

console.log(failures === 0 ? "\nOK: обрезки и разной высоты в рядах нет." : `\nFAIL: замечаний — ${failures}`);
process.exit(failures === 0 ? 0 : 1);
