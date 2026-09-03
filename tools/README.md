# Regenerating the Psalms asset

`app/src/main/assets/psalms_he.json` holds all 150 chapters of Tehillim in
Hebrew, and is built from the Westminster Leningrad Codex (Open Scriptures
`morphhb`, OSIS XML). Cantillation marks are stripped, vowel points kept.

```
curl -o Ps.xml https://raw.githubusercontent.com/openscriptures/morphhb/master/wlc/Ps.xml
python3 wlc_extract.py        # Ps.xml      -> psalms_full.json
python3 build_psalms_asset.py # psalms_full -> psalms_he.json
mv psalms_he.json ../app/src/main/assets/
```

Each entry is `{"c": chapter, "ref": Hebrew reference, "len": characters, "text": …}`.
`len` counts characters with the vowel points removed, which is the figure
`PsalmRepository.MAX_CHARS` filters on.
