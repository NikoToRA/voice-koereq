#!/usr/bin/env python3
"""
voice-koereq 実装ファイル同期スクリプト
"""

import subprocess
import os

def sync_claude_implementations():
    """Claudeが生成した実装をmainブランチに同期"""
    
    # Claudeブランチのリスト
    claude_branches = [
        ("claude/issue-2-20250602_100108", "F1", "起動画面"),
        ("claude/issue-3-20250602_101312", "F2", "音声録音"),
        ("claude/issue-4-20250602_101312", "F3", "文字起こし"),
        ("claude/issue-5-20250602_101323", "F4", "AI医療アシスタント"),
    ]
    
    # 現在のブランチを保存
    result = subprocess.run(['git', 'branch', '--show-current'], capture_output=True, text=True)
    original_branch = result.stdout.strip()
    
    for branch, feature_id, feature_name in claude_branches:
        print(f"\n🔄 Syncing {feature_id} ({feature_name}) from {branch}")
        
        try:
            # ファイルをチェックアウト
            cmd_ios = ['git', 'checkout', f'origin/{branch}', '--', f'ios/VoiceKoereq/**/{feature_id}*', f'ios/VoiceKoereqTests/{feature_id}*']
            cmd_android = ['git', 'checkout', f'origin/{branch}', '--', f'android/**/{feature_id}*']
            
            # iOSファイル
            result = subprocess.run(cmd_ios, capture_output=True, text=True)
            if result.returncode == 0:
                print(f"  ✅ iOS files synced")
            
            # Androidファイル
            result = subprocess.run(cmd_android, capture_output=True, text=True)
            if result.returncode == 0:
                print(f"  ✅ Android files synced")
                
        except Exception as e:
            print(f"  ❌ Error syncing {feature_id}: {e}")
    
    # 変更をコミット
    subprocess.run(['git', 'add', '.'])
    subprocess.run(['git', 'status', '--short'])
    
    print("\n📋 Sync complete! Review changes and commit if needed.")

if __name__ == "__main__":
    sync_claude_implementations()