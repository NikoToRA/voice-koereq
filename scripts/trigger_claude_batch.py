#!/usr/bin/env python3
"""
voice-koereq Claude一括実行スクリプト
"""

import subprocess
import json
import time

def trigger_claude_on_issues(issue_numbers):
    """指定されたIssueでClaudeを起動"""
    
    for issue_num in issue_numbers:
        print(f"🤖 Triggering Claude on Issue #{issue_num}")
        
        # Issueにコメントを追加
        comment = f"@claude Please implement this feature as described above. Focus on creating production-ready code for both iOS and Android platforms."
        
        cmd = ['gh', 'issue', 'comment', str(issue_num), '--body', comment]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, check=True)
            print(f"✅ Comment added to Issue #{issue_num}")
            
            # 次のIssueまで少し待機（API制限対策）
            if len(issue_numbers) > 1:
                print("   ⏳ Waiting 30 seconds before next trigger...")
                time.sleep(30)
                
        except subprocess.CalledProcessError as e:
            print(f"❌ Failed to comment on Issue #{issue_num}: {e.stderr}")

def get_unimplemented_issues():
    """未実装のIssueを取得"""
    cmd = ['gh', 'issue', 'list', '--json', 'number,title,labels', '--limit', '50']
    
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        issues = json.loads(result.stdout)
        
        unimplemented = []
        for issue in issues:
            labels = [l['name'] for l in issue['labels']]
            
            # 実装済みでない かつ 機能Issue である
            if 'implemented' not in labels and any(l in ['feature', 'ai_service', 'ui', 'audio_capture', 'backend'] for l in labels):
                unimplemented.append(issue['number'])
                
        return unimplemented
        
    except subprocess.CalledProcessError as e:
        print(f"❌ Failed to fetch issues: {e.stderr}")
        return []

if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1:
        # 引数で指定されたIssue番号
        issue_numbers = [int(x.strip()) for x in sys.argv[1].split(',')]
    else:
        # 未実装のIssueを自動取得
        issue_numbers = get_unimplemented_issues()
        
        if not issue_numbers:
            print("⚠️  No unimplemented issues found")
            sys.exit(0)
            
        print(f"📋 Found {len(issue_numbers)} unimplemented issues: {issue_numbers}")
        confirm = input("🤔 Trigger Claude on all these issues? (y/N): ")
        
        if confirm.lower() != 'y':
            print("❌ Cancelled")
            sys.exit(0)
    
    trigger_claude_on_issues(issue_numbers)