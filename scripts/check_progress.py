#!/usr/bin/env python3
"""
voice-koereq 実装進捗チェッカー
"""

import yaml
import json
from pathlib import Path
import sys

def check_progress():
    # 仕様書を読み込み
    with open('voice-koereq-spec.yaml', 'r', encoding='utf-8') as f:
        spec = yaml.safe_load(f)
    
    implemented = []
    not_implemented = []
    
    for feature in spec['features']:
        # 実装済みかチェック（iOSファイルの存在で判断）
        check_path = Path(f"ios/VoiceKoereq/Views/{feature['id']}View.swift")
        
        if check_path.exists():
            implemented.append(feature['id'])
        else:
            not_implemented.append(feature['id'])
    
    # 進捗レポート
    total = len(spec['features'])
    done = len(implemented)
    progress = (done / total) * 100 if total > 0 else 0
    
    print(f"📊 実装進捗: {done}/{total} ({progress:.1f}%)")
    print(f"✅ 実装済み: {', '.join(implemented)}")
    print(f"⏳ 未実装: {', '.join(not_implemented)}")
    
    # GitHub Actions 出力
    if not_implemented:
        print(f"::set-output name=next_feature::{not_implemented[0]}")
    else:
        print("::set-output name=next_feature::")
    
    # 進捗をJSONで保存
    progress_data = {
        'total_features': total,
        'implemented': done,
        'progress_percentage': progress,
        'implemented_features': implemented,
        'pending_features': not_implemented
    }
    
    with open('progress.json', 'w') as f:
        json.dump(progress_data, f, indent=2)

if __name__ == "__main__":
    check_progress()