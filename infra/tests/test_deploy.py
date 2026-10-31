import os
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class DeployTests(unittest.TestCase):
    def test_receiver_rejects_arbitrary_commands_before_touching_server(self):
        for command in ('', 'bash', 'deploy ../bad', 'deploy ' + 'a' * 40 + '; id',
                        'deploy ' + 'A' * 40):
            with self.subTest(command=command):
                result = subprocess.run(
                    ['bash', str(ROOT / 'infra/deploy-receive.sh')],
                    env={**os.environ, 'SSH_ORIGINAL_COMMAND': command},
                    capture_output=True)
                self.assertNotEqual(result.returncode, 0)

    def test_sender_rejects_bad_revision(self):
        result = subprocess.run(
            ['bash', str(ROOT / 'infra/deploy-send.sh'), '/missing', '../bad'],
            capture_output=True)
        self.assertNotEqual(result.returncode, 0)

    def test_sender_requires_explicit_secrets(self):
        env = {k: v for k, v in os.environ.items() if not k.startswith('DEPLOY_')}
        result = subprocess.run(
            ['bash', str(ROOT / 'infra/deploy-send.sh'), '/missing', 'a' * 40],
            env=env, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b'DEPLOY_HOST', result.stderr)


if __name__ == '__main__':
    unittest.main()
