#!/bin/bash

echo "🔄 Updating Claude OAuth tokens"
echo ""
echo "Please get new tokens from: https://console.anthropic.com/"
echo ""

# Update Claude Access Token
echo "Enter new Claude OAuth Access Token:"
read -s CLAUDE_ACCESS_TOKEN
echo $CLAUDE_ACCESS_TOKEN | gh secret set CLAUDE_ACCESS_TOKEN

# Update Claude Refresh Token
echo ""
echo "Enter new Claude OAuth Refresh Token:"
read -s CLAUDE_REFRESH_TOKEN
echo $CLAUDE_REFRESH_TOKEN | gh secret set CLAUDE_REFRESH_TOKEN

# Update Claude Token Expiration
echo ""
echo "Enter new Claude Token Expires At (timestamp):"
read CLAUDE_EXPIRES_AT
echo $CLAUDE_EXPIRES_AT | gh secret set CLAUDE_EXPIRES_AT

echo ""
echo "✅ Claude OAuth tokens updated!"
echo ""
echo "Let's trigger Claude again..."

# Trigger Claude on the issue
gh issue comment 2 --body "@claude Please implement the voice-koereq startup screen (F1) as described in the requirements above."