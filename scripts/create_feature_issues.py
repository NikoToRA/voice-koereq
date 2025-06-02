#!/usr/bin/env python3
"""
voice-koereq 機能別Issue作成スクリプト
"""

import subprocess
import yaml

def create_feature_issues():
    """仕様書から機能別Issueを作成"""
    
    # 仕様書を読み込み
    with open('voice-koereq-spec.yaml', 'r', encoding='utf-8') as f:
        spec = yaml.safe_load(f)
    
    features = spec.get('features', [])
    
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
            '--label', f'feature,{feature_type},{priority}'
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