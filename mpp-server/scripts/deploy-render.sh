#!/bin/bash
# 部署到 Render.com 的快速指南脚本

set -e

echo "🚀 MPP-Server Render 部署指南"
echo "================================"
echo ""

echo "📋 前置条件检查..."

# 检查是否有 render.yaml
if [ ! -f "../render.yaml" ]; then
    echo "❌ render.yaml 文件不存在"
    exit 1
fi

echo "✅ 配置文件存在"
echo ""

echo "🔑 需要准备的信息："
echo "  1. GitHub 账号（用于连接仓库）"
echo "  2. OpenAI API Key 或其他 LLM API Key"
echo ""

echo "📝 部署步骤："
echo ""
echo "方法 1: 使用 render.yaml（推荐）"
echo "================================"
echo "1. 访问 https://dashboard.render.com"
echo "2. 点击 'New +' → 'Blueprint'"
echo "3. 连接你的 GitHub 仓库"
echo "4. Render 会自动检测 render.yaml"
echo "5. 在环境变量中设置 OPENAI_API_KEY"
echo "6. 点击 'Apply' 开始部署"
echo ""

echo "方法 2: 手动创建 Web Service"
echo "================================"
echo "1. 访问 https://dashboard.render.com"
echo "2. 点击 'New +' → 'Web Service'"
echo "3. 连接你的 GitHub 仓库"
echo "4. 配置如下："
echo "   - Name: mpp-server"
echo "   - Region: Singapore"
echo "   - Branch: master"
echo "   - Build Command: ./gradlew :mpp-server:fatJar"
echo "   - Start Command: java -Xmx512m -jar mpp-server/build/libs/mpp-server-*-all.jar"
echo "   - Instance Type: Free"
echo "5. 添加环境变量："
echo "   - OPENAI_API_KEY=sk-xxx"
echo "   - SERVER_PORT=8080"
echo "6. 点击 'Create Web Service'"
echo ""

echo "⏱️  预计部署时间："
echo "  - 首次构建: 5-8 分钟"
echo "  - 后续部署: 2-3 分钟"
echo ""

echo "🧪 部署完成后测试："
echo "  curl https://mpp-server-xxx.onrender.com/health"
echo ""

echo "📊 Render 免费层限制："
echo "  - 512MB RAM"
echo "  - 共享 CPU"
echo "  - 750 小时/月"
echo "  - 15分钟无请求后休眠（首次请求需等待30秒唤醒）"
echo ""

echo "💡 提示："
echo "  - 如需避免休眠，可以设置 Cron Job 定期 ping 健康检查端点"
echo "  - 如需更多资源，可以升级到 Starter 计划（$7/月）"
echo ""

echo "🔗 有用的链接："
echo "  - Render Dashboard: https://dashboard.render.com"
echo "  - Render Docs: https://render.com/docs"
echo "  - 部署指南: ../mpp-server/DEPLOY-ALTERNATIVES.md"
echo ""

