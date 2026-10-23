#!/usr/bin/env python3
"""Enable isolated host-Compose evidence on a disposable checkout; no runtime dependency changes."""
from pathlib import Path
import argparse
parser = argparse.ArgumentParser()
parser.add_argument('checkout', type=Path)
args = parser.parse_args()
source = Path(__file__).resolve().parent
build = args.checkout / 'app/build.gradle.kts'
marker = '// Isolated host rendering experiment;'
text = build.read_text()
if marker not in text:
    build.write_text(text + '\n' + (source / 'gradle-fragment.txt').read_text())
target = args.checkout / 'app/src/test/java/com/my/firstapp/MealUiSnapshotTest.kt'
target.write_text((source / 'MealUiSnapshotTest.kt').read_text())
print('Enabled host render harness. Run testDebugUnitTest --tests "*MealUiSnapshotTest" on this disposable checkout.')
