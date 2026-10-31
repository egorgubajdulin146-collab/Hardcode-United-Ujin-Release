import os
import subprocess
import unittest
import tempfile
import tarfile
import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

MOCK = '''#!/usr/bin/env python3
import os,sys
args=sys.argv[1:]; tool=os.path.basename(sys.argv[0]); mode=os.environ['MODE']
with open(os.environ['CALLS'],'a') as log: log.write(tool+' '+' '.join(args)+'\\n')
if tool=='docker':
    if args[0]=='inspect':
        print('bad-revision' if mode=='revision' else os.environ['REVISION'])
    elif 'exec' in args:
        if mode=='backup': sys.exit(1)
        print('mock-dump')
    elif 'up' in args and mode=='up': sys.exit(1)
    elif 'port' in args: print(os.environ.get('BINDING','127.0.0.1:19191'))
    elif 'ps' in args: print('container')
else:
    assert '--connect-timeout' in args and '--max-time' in args
    assert args[-1].startswith('http://127.0.0.1:19191/')
    if mode=='health': sys.exit(28)
    if '--write-out' in args: print('401',end='')
'''


class DeployTests(unittest.TestCase):
    def run_receiver(self, mode, binding='127.0.0.1:19191'):
        # Isolated filesystem and fake executables: no Docker, SSH or live env.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'bin').mkdir()
            for tool in ('docker', 'curl'):
                executable = root / 'bin' / tool
                executable.write_text(MOCK)
                executable.chmod(0o700)
            script = (ROOT / 'infra/deploy-receive.sh').read_text()
            for old, new in (('/opt/ujin', str(root / 'app')),
                             ('/var/backups/ujin', str(root / 'backups')),
                             ('/run/lock/ujin-deploy.lock', str(root / 'lock')),
                             ('/etc/ujin/local.env', str(root / 'local.env'))):
                script = script.replace(old, new)
            receiver = root / 'receiver.sh'
            receiver.write_text(script)
            (root / 'app').mkdir()
            (root / 'app/current').symlink_to('previous')
            archive = io.BytesIO()
            with tarfile.open(fileobj=archive, mode='w:gz') as bundle:
                member = tarfile.TarInfo('.dockerignore')
                member.size = 2
                bundle.addfile(member, io.BytesIO(b'**'))
            revision = 'b' * 40
            result = subprocess.run(['bash', str(receiver)], input=archive.getvalue(),
                env={**os.environ, 'PATH': str(root / 'bin') + ':' + os.environ['PATH'],
                     'MODE': mode, 'BINDING': binding, 'REVISION': revision,
                     'CALLS': str(root / 'calls'),
                     'SSH_ORIGINAL_COMMAND': 'deploy ' + revision}, capture_output=True)
            calls = (root / 'calls').read_text()
            pointer = os.readlink(root / 'app/current')
            if mode == 'success' and binding.startswith('127.0.0.1:'):
                self.assertEqual(result.returncode, 0, result.stderr.decode())
                self.assertTrue(pointer.endswith('/releases/' + revision))
                self.assertIn('http://127.0.0.1:19191/api/auth/csrf', calls)
                self.assertEqual((root / 'backups' / ('before-' + revision + '.dump')).stat().st_mode & 0o777, 0o600)
            else:
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(pointer, 'previous')
            if mode == 'backup':
                self.assertNotIn(' up ', calls)

    def test_success_and_custom_port(self):
        self.run_receiver('success')

    def test_failures_do_not_publish_current(self):
        for mode in ('backup', 'up', 'health', 'revision'):
            with self.subTest(mode=mode):
                self.run_receiver(mode)

    def test_public_binding_rejected(self):
        self.run_receiver('success', '0.0.0.0:19191')

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
