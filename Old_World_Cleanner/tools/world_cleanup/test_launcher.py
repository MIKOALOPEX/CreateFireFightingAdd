"""Exercise the packaged PowerShell flow with synthetic worlds only."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_clean_world import world, snapshot


@unittest.skipUnless(os.name == 'nt' and shutil.which('powershell.exe'), 'Windows launcher')
class LauncherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.tool = self.root / 'tool'
        self.tool.mkdir()
        project = Path(__file__).resolve().parents[2]
        shutil.copy2(project / 'clean-world.ps1', self.tool)
        modules = self.tool / 'tools/world_cleanup'
        modules.mkdir(parents=True)
        for path in (project / 'tools/world_cleanup').glob('*.py'):
            shutil.copy2(path, modules)
        self.source = self.root / 'source'
        world(self.source)
        self.export = self.root / 'export'
        self.export.mkdir()

    def run_launcher(self, answers='', *args):
        return subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                               '-File', str(self.tool / 'clean-world.ps1'), *args],
                              input=answers, text=True, capture_output=True, timeout=60)

    def test_export_first_and_complete_once(self):
        before = snapshot(self.source)
        result = self.run_launcher(f'{self.export}\n{self.source}\n')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertLess(result.stdout.index('Step 1'), result.stdout.index('Step 2'))
        self.assertIn('BACK UP YOUR WORLD', result.stdout)
        self.assertIn('locally on your computer', result.stdout)
        self.assertIn('Please close this window.', result.stdout)
        self.assertTrue(result.stdout.isascii(), result.stdout)
        self.assertEqual(snapshot(self.source), before)
        self.assertEqual(len(list(self.export.iterdir())), 1)
        self.assertTrue((self.tool / 'LAST_SUCCESSFUL_CLEANUP.txt').exists())

    def test_invalid_export_stops_before_source_selection(self):
        result = self.run_launcher(str(self.root / 'missing') + '\n')
        self.assertEqual(result.returncode, 2)
        self.assertNotIn('Step 2', result.stdout)
        self.assertIn('Export directory does not exist', result.stdout)
        self.assertFalse((self.tool / 'LAST_SUCCESSFUL_CLEANUP.txt').exists())

    def test_explicit_arguments_and_dry_run_remain_supported(self):
        result = self.run_launcher('', '-Source', str(self.source), '-Output',
                                   str(self.export / 'chosen'), '-DryRun')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('Dry run complete', result.stdout)
        self.assertEqual(list(self.export.iterdir()), [])
