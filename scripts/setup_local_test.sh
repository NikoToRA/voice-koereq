#!/bin/bash
"""
voice-koereq ローカルテスト環境セットアップスクリプト
"""

echo "🚀 voice-koereq Local Test Environment Setup"
echo "==========================================="
echo ""

# カラー定義
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# プロジェクトルートの確認
if [ ! -f "voice-koereq-spec.yaml" ]; then
    echo -e "${RED}❌ Error: voice-koereq-spec.yaml not found${NC}"
    echo "Please run this script from the project root directory."
    exit 1
fi

echo "📁 Project Structure Check"
echo "--------------------------"

# iOS ファイルチェック
echo -e "\n${YELLOW}iOS Implementation:${NC}"
ios_files=(
    "ios/VoiceKoereq/Views/F1View.swift"
    "ios/VoiceKoereq/Views/F2View.swift"
    "ios/VoiceKoereq/Views/F3View.swift"
    "ios/VoiceKoereq/Views/F4View.swift"
)

ios_count=0
for file in "${ios_files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "  ✅ $file"
        ((ios_count++))
    else
        echo -e "  ❌ $file - Missing"
    fi
done

# Android ファイルチェック
echo -e "\n${YELLOW}Android Implementation:${NC}"
android_files=(
    "android/app/src/main/kotlin/com/voicekoereq/ui/F1Screen.kt"
    "android/app/src/main/kotlin/com/voicekoereq/ui/F2Screen.kt"
    "android/app/src/main/kotlin/com/voicekoereq/ui/F3Screen.kt"
    "android/app/src/main/kotlin/com/voicekoereq/ui/F4Screen.kt"
)

android_count=0
for file in "${android_files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "  ✅ $file"
        ((android_count++))
    else
        echo -e "  ❌ $file - Missing"
    fi
done

# 統計
echo -e "\n${YELLOW}📊 Implementation Status:${NC}"
echo "  iOS Files: $ios_count/4"
echo "  Android Files: $android_count/4"
total_count=$((ios_count + android_count))
echo -e "  ${GREEN}Total: $total_count/8${NC}"

# Git状態チェック
echo -e "\n${YELLOW}Git Status:${NC}"
branch=$(git branch --show-current)
echo "  Current Branch: $branch"
uncommitted=$(git status --porcelain | wc -l)
if [ $uncommitted -eq 0 ]; then
    echo -e "  ${GREEN}✅ Working tree clean${NC}"
else
    echo -e "  ${YELLOW}⚠️  $uncommitted uncommitted changes${NC}"
fi

# 必要なツールチェック
echo -e "\n${YELLOW}Required Tools:${NC}"
tools=(
    "gh:GitHub CLI"
    "git:Git"
    "python3:Python 3"
)

for tool_spec in "${tools[@]}"; do
    IFS=':' read -r tool name <<< "$tool_spec"
    if command -v $tool &> /dev/null; then
        version=$($tool --version | head -n1)
        echo -e "  ✅ $name: $version"
    else
        echo -e "  ❌ $name: Not installed"
    fi
done

# テストコマンド生成
echo -e "\n${YELLOW}📝 Quick Test Commands:${NC}"
echo "  # Check issue status:"
echo "  python3 scripts/issue_dashboard.py"
echo ""
echo "  # View implementation progress:"
echo "  cat PROGRESS.md"
echo ""
echo "  # Run quick test:"
echo "  ./scripts/quick_test.sh"

echo -e "\n${GREEN}✅ Setup check complete!${NC}"