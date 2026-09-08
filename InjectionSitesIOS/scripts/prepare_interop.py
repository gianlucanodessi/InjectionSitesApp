"""Build a JVM harness using the unchanged Android codec and extracted Android models."""
from pathlib import Path
import re
import shutil

root = Path(__file__).resolve().parents[2]
source = root/'InjectionSitesAndroid_v2/app/src/main/java/com/example/injectionsites'
destination = root/'InjectionSitesIOS/Interop/src/main/kotlin/generated'
destination.mkdir(parents=True, exist_ok=True)
shutil.copyfile(source/'BackupManager.kt', destination/'BackupManager.kt')
text = (source/'MainActivity.kt').read_text(encoding='utf-8')
enums = text[text.index('enum class EntryMode'):text.index('data class RecordItem')]
records = text[text.index('data class RecordItem'):text.index('internal fun loadRecords')]
settings = text[text.index('internal data class TimingSettings'):text.index('internal val DefaultSettings')]
resources = sorted(set(re.findall(r'R.drawable.(\w+)', enums)))
resource_stub = 'object R { object drawable {\n' + '\n'.join(f'const val {name} = {i}' for i,name in enumerate(resources)) + '\n} }\n'
header = 'package com.example.injectionsites\nimport java.util.UUID\nimport org.json.JSONObject\ndata class Color(val value: Long)\nprivate val Blue = Color(0xFF1557C0)\n'
(destination/'Models.kt').write_text(header+resource_stub+settings+enums+records, encoding='utf-8')
print('Prepared unchanged BackupManager.kt and Android model/serialization declarations')
