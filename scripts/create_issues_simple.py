#!/usr/bin/env python3
"""
voice-koereq Issue作成スクリプト（簡易版）
"""

import subprocess

# 残りの機能定義
features = [
    {
        "id": "F7",
        "name": "サマリー生成",
        "description": "会話内容の要約とレポート生成",
        "type": "ai_service",
        "priority": "medium"
    },
    {
        "id": "F8",
        "name": "オフライン",
        "description": "オフライン時のデータ保存と同期機能",
        "type": "infrastructure",
        "priority": "low"
    }
]

def create_feature_issues():
    """機能別Issueを作成"""
    
    for feature in features:
        feature_id = feature['id']
        name = feature['name']
        description = feature['description']
        feature_type = feature['type']
        priority = feature['priority']
        
        # Issue本文を作成
        body = f"""## 概要
{description}

## 機能詳細
- **機能ID**: {feature_id}
- **機能名**: {name}
- **タイプ**: {feature_type}
- **優先度**: {priority}

## 実装要件

### iOS
- SwiftUI + Combine
- iOS 16+ 対応
- MVVM アーキテクチャ
- エラーハンドリング
- 日本語UI

### Android
- Jetpack Compose + Kotlin
- Material Design 3
- Hilt DI
- Coroutines + Flow
- 日本語UI

### 共有ロジック
- Kotlin Multiplatform
- expect/actual パターン
- 共通API設計

## 受け入れ条件
- [ ] iOS実装完了
- [ ] Android実装完了
- [ ] テストコード作成
- [ ] コードレビュー完了
- [ ] 日本語UI確認

---
*このIssueは自動生成されました*"""

        title = f"[{feature_id}] {name}"
        
        # Issueを作成
        cmd = [
            'gh', 'issue', 'create',
            '--title', title,
            '--body', body,
            '--label', 'feature'
        ]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, check=True)
            issue_url = result.stdout.strip()
            print(f"✅ Created: {title}")
            print(f"   URL: {issue_url}")
        except subprocess.CalledProcessError as e:
            print(f"❌ Failed to create: {title}")
            print(f"   Error: {e.stderr}")

if __name__ == "__main__":
    create_feature_issues()