#!/usr/bin/env python3
"""Reject SPA fallback or missing JS/CSS after deployment."""
import sys
from html.parser import HTMLParser
from urllib.parse import urljoin, urlsplit
from urllib.request import urlopen


class Assets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'script' and attrs.get('src'):
            self.paths.append((attrs['src'], 'javascript'))
        if tag == 'link' and attrs.get('rel') == 'stylesheet':
            self.paths.append((attrs['href'], 'css'))


def check(base):
    with urlopen(base + '/', timeout=20) as response:
        parser = Assets()
        parser.feed(response.read().decode())
    if not any(kind == 'javascript' for _, kind in parser.paths):
        raise ValueError('Index has no JavaScript asset')
    for path, kind in parser.paths:
        url = urljoin(base + '/', path)
        if urlsplit(url).netloc != urlsplit(base).netloc:
            raise ValueError('Unexpected cross-origin static asset')
        with urlopen(url, timeout=20) as response:
            body = response.read()
            mime = response.headers.get_content_type()
        allowed = ('application/javascript', 'text/javascript') if kind == 'javascript' else ('text/css',)
        if mime not in allowed or not body or body.lstrip().lower().startswith(b'<!doctype html'):
            raise ValueError('Static asset is missing or returned HTML: ' + path)
    print('Static JS/CSS responses PASS')


if __name__ == '__main__':
    check(sys.argv[1].rstrip('/'))
