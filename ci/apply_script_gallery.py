from pathlib import Path

path = Path("app/src/main/java/com/desarrollamo/storeamo/MainActivityV2.kt")
text = path.read_text(encoding="utf-8")
old = "if (termuxInstalled) TermuxReadyCard() else TermuxInstallCard("
new = "if (termuxInstalled) TermuxScriptGalleryCard(context) { notice = it } else TermuxInstallCard("
if new in text:
    print("StoreAMO script gallery already wired")
elif old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("StoreAMO script gallery wired")
else:
    raise SystemExit("Expected TermuxReadyCard call not found")
