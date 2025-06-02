#!/usr/bin/env python3
"""
voice-koereq Issue管理ダッシュボード
"""

import subprocess
import json
from datetime import datetime

def show_dashboard():
    print("=" * 60)
    print("🎤 voice-koereq Issue Dashboard")
    print("=" * 60)
    print(f"📅 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()
    
    # 全Issueを取得
    cmd = ['gh', 'issue', 'list', '--json', 'number,title,labels,state,createdAt,url', '--limit', '50']
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    if result.returncode != 0:
        print("❌ Failed to fetch issues")
        return
    
    issues = json.loads(result.stdout)
    
    # カテゴリ分け
    not_started = []
    ready_to_implement = []
    implemented = []
    
    for issue in issues:
        labels = [l['name'] for l in issue['labels']]
        
        if 'implemented' in labels:
            implemented.append(issue)
        elif 'ready-to-implement' in labels:
            ready_to_implement.append(issue)
        else:
            not_started.append(issue)
    
    # 統計表示
    total = len(issues)
    print(f"📊 統計")
    print(f"  総Issue数: {total}")
    print(f"  ✅ 実装済み: {len(implemented)} ({len(implemented)/total*100:.1f}%)")
    print(f"  🔄 実装中: {len(ready_to_implement)}")
    print(f"  ⏳ 未着手: {len(not_started)}")
    print()
    
    # 各カテゴリの詳細
    if not_started:
        print("⏳ 未着手のIssue:")
        for issue in not_started:
            print(f"  #{issue['number']:3d} {issue['title']}")
    print()
    
    if ready_to_implement:
        print("🔄 実装中のIssue:")
        for issue in ready_to_implement:
            print(f"  #{issue['number']:3d} {issue['title']}")
    print()
    
    if implemented:
        print("✅ 実装済みのIssue:")
        for issue in implemented[:5]:  # 最新5件のみ表示
            print(f"  #{issue['number']:3d} {issue['title']}")
        if len(implemented) > 5:
            print(f"  ... 他 {len(implemented)-5} 件")
    print()
    
    # 関連するPRを取得
    print("📝 最新のPR:")
    pr_cmd = ['gh', 'pr', 'list', '--json', 'number,title,state,createdAt', '--limit', '5']
    pr_result = subprocess.run(pr_cmd, capture_output=True, text=True)
    
    if pr_result.returncode == 0:
        prs = json.loads(pr_result.stdout)
        for pr in prs:
            state_icon = "🟢" if pr['state'] == 'OPEN' else "🟣"
            print(f"  {state_icon} PR #{pr['number']:3d} {pr['title']}")
    print()
    
    # アクション可能な項目
    print("💡 次のアクション:")
    if not_started:
        print(f"  1. 未着手のIssueに ready-to-implement ラベルを付けて実装開始")
        print(f"     gh issue edit {not_started[0]['number']} --add-label ready-to-implement")
    if ready_to_implement:
        print(f"  2. 実装中のIssueの進捗確認")
        print(f"     gh issue view {ready_to_implement[0]['number']}")
    print(f"  3. 全Issueを一括実装")
    print(f"     gh workflow run '🚀 Batch Issue Implementation'")
    
    print()
    print("=" * 60)

def create_milestone():
    """マイルストーンを作成"""
    milestones = [
        {"title": "MVP", "description": "最小限の動作可能な製品", "due_on": "2024-12-31"},
        {"title": "Beta", "description": "ベータ版リリース", "due_on": "2025-01-15"},
        {"title": "Production", "description": "本番リリース", "due_on": "2025-01-31"}
    ]
    
    for ms in milestones:
        cmd = [
            'gh', 'api', 'repos/:owner/:repo/milestones',
            '-f', f"title={ms['title']}",
            '-f', f"description={ms['description']}",
            '-f', f"due_on={ms['due_on']}"
        ]
        subprocess.run(cmd)
        print(f"✅ Created milestone: {ms['title']}")

if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1 and sys.argv[1] == 'milestone':
        create_milestone()
    else:
        show_dashboard()