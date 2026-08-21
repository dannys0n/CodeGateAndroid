# CodeGate Android content pack

This directory is a complete, portable content export for the native Android Algorithm Assembly prototype.
It contains problem text and pre-generated C++/Python assembly lessons. It does not require the CodeGate
source repositories, Docker, CoJudge, Node.js, or a compiler at Android runtime.

## Copy into Android Studio

Copy this entire directory to:

`app/src/main/assets/codegate/`

Read `index.json` first. Each listed shard is UTF-8 JSON compressed with gzip. Android can open a shard with
`context.assets.open("codegate/<file>")` and `java.util.zip.GZIPInputStream`.

The correct answer is `correctOrder`. Shuffle the block IDs on-device. Display `displayCode`; retain
`sourceCode` only to reconstruct the exact reference solution after completion.

Regenerate from desktop CodeGate with:

`npm run codegate:export:android`
