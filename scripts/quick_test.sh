#!/bin/bash
"""
voice-koereq クイックテストスクリプト
"""

echo "⚡ voice-koereq Quick Test"
echo "========================="
echo ""

# カラー定義
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 実装ファイル数チェック
echo -e "${BLUE}📁 Implementation Files:${NC}"
ios_files=$(find ios -name "*.swift" 2>/dev/null | wc -l)
android_files=$(find android -name "*.kt" 2>/dev/null | wc -l)
echo "  iOS Swift files: $ios_files"
echo "  Android Kotlin files: $android_files"

# GitHub状態チェック
echo -e "\n${BLUE}🔗 GitHub Status:${NC}"
if command -v gh &> /dev/null; then
    # Issues
    open_issues=$(gh issue list --state open --json number | jq length 2>/dev/null || echo "?")
    closed_issues=$(gh issue list --state closed --json number | jq length 2>/dev/null || echo "?")
    echo "  Open Issues: $open_issues"
    echo "  Closed Issues: $closed_issues"
    
    # PRs
    open_prs=$(gh pr list --state open --json number | jq length 2>/dev/null || echo "?")
    merged_prs=$(gh pr list --state merged --json number | jq length 2>/dev/null || echo "?")
    echo "  Open PRs: $open_prs"
    echo "  Merged PRs: $merged_prs"
    
    # Recent workflow runs
    echo -e "\n${BLUE}🚀 Recent Workflow Runs:${NC}"
    gh run list --limit 3 --json displayTitle,status,conclusion,createdAt | \
    jq -r '.[] | "  \(.status | if . == "completed" then "✅" else "🔄" end) \(.displayTitle) (\(.createdAt | split("T")[0]))"' 2>/dev/null || \
    echo "  ❌ Unable to fetch workflow runs"
    
else
    echo -e "  ${RED}❌ GitHub CLI not available${NC}"
fi

# プロジェクト構造チェック
echo -e "\n${BLUE}📊 Project Structure:${NC}"
feature_count=0
for feature in F1 F2 F3 F4; do
    ios_view="ios/VoiceKoereq/Views/${feature}View.swift"
    android_ui="android/app/src/main/kotlin/com/voicekoereq/ui/${feature}Screen.kt"
    
    if [ -f "$ios_view" ] && [ -f "$android_ui" ]; then
        echo -e "  ✅ $feature: iOS + Android"
        ((feature_count++))
    elif [ -f "$ios_view" ]; then
        echo -e "  🟡 $feature: iOS only"
    elif [ -f "$android_ui" ]; then
        echo -e "  🟡 $feature: Android only"
    else
        echo -e "  ❌ $feature: Missing"
    fi
done

echo -e "\n${BLUE}📈 Completion Status:${NC}"
completion_percent=$((feature_count * 100 / 4))
echo -e "  ${GREEN}Implemented: $feature_count/4 features ($completion_percent%)${NC}"

# コード統計
echo -e "\n${BLUE}📝 Code Statistics:${NC}"
if [ -d ios ] && [ -d android ]; then
    ios_lines=$(find ios -name "*.swift" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
    android_lines=$(find android -name "*.kt" -exec wc -l {} + 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
    total_lines=$((ios_lines + android_lines))
    
    echo "  iOS Swift: $ios_lines lines"
    echo "  Android Kotlin: $android_lines lines"
    echo -e "  ${GREEN}Total: $total_lines lines${NC}"
fi

# 次のアクション
echo -e "\n${YELLOW}💡 Next Actions:${NC}"
if [ $feature_count -lt 4 ]; then
    echo "  1. Check Claude PR Assistant status: gh run list --workflow=\"Claude PR Assistant\""
    echo "  2. View issue dashboard: python3 scripts/issue_dashboard.py"
    echo "  3. Trigger remaining implementations"
else
    echo "  1. Review and test generated code"
    echo "  2. Check for F5-F8 implementations"
    echo "  3. Prepare for deployment"
fi

echo -e "\n${GREEN}⚡ Quick test complete!${NC}"