#!/bin/bash

cd "/home/mubashir/projects/Final deliverable/Android Project"

echo "🧹 CLEANING UP IRRELEVANT FILES..."
echo ""

# Remove all MD files
echo "Removing .md files..."
rm -f ACTION_REQUIRED.md
rm -f BUILD_ERRORS_FIXED.md
rm -f BUILD_ERROR_FIXED.md
rm -f COMPREHENSIVE_TEST_GUIDE.md
rm -f ERROR_RESOLUTION_CHECKLIST.md
rm -f FIREBASE_AUTH_FIX.md
rm -f HOW_TO_RUN.md
rm -f PROJECT_STATUS.md
rm -f QUICK_FIX.md
rm -f QUICK_START_MOBILE.md
rm -f README_GITHUB.md
rm -f README.md
rm -f TROUBLESHOOTING.md
rm -f UI_IMPROVEMENTS.md

# Remove script files
echo "Removing script files..."
rm -f push_to_github.sh
rm -f git_push.sh

# Remove text files
echo "Removing text files..."
rm -f PUSH_INSTRUCTIONS.txt
rm -f READY_TO_PUSH.txt

# Remove this cleanup script itself
rm -f cleanup.sh

echo ""
echo "✅ Cleanup complete!"
echo ""
echo "Files removed:"
echo "  - 14 .md files"
echo "  - 2 .sh script files"
echo "  - 2 .txt instruction files"
echo ""
echo "✅ Your project is now clean and ready to push!"
