# -*- coding: utf-8 -*-
import os, sys

LANGS = ["en", "de", "pl", "es", "fr", "it", "pt", "ru", "uk", "zh", "ja", "nl"]

# Read base english, german, polish, spanish from existing file to preserve verified texts
import re

with open("launcher_apk/src/org/tontos/launcher/I18n.java", "r", encoding="utf-8") as f:
    orig = f.read()

def extract_lang(code):
    match = re.search(rf'Map<String,\s*String>\s+{code}\s*=\s*new\s*HashMap.*?;(.*?)(?:TRANSLATIONS\.put\("{code}"|// ==)', orig, re.DOTALL)
    if not match:
        return {}
    block = match.group(1)
    res = {}
    for line in block.splitlines():
        m = re.search(r'\.put\((KEY_\w+),\s*"(.*?)"\);', line)
        if m:
            res[m.group(1)] = m.group(2)
    return res

T = {
    "en": extract_lang("en"),
    "de": extract_lang("de"),
    "pl": extract_lang("pl"),
    "es": extract_lang("es"),
    "fr": extract_lang("fr"),
    "it": extract_lang("it"),
    "pt": extract_lang("pt"),
    "ru": extract_lang("ru"),
    "uk": extract_lang("uk"),
    "zh": {},
    "ja": {},
    "nl": {}
}

print(f"Extracted initial keys: en={len(T['en'])}, de={len(T['de'])}, pl={len(T['pl'])}, es={len(T['es'])}, fr={len(T['fr'])}")
