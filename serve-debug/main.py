#!/usr/bin/env python3
# main.py - 完整改造后的API & 调试看板
import json
from typing import List
from fastapi import Depends, HTTPException, Query, Request, status, FastAPI
from fastapi.responses import JSONResponse, HTMLResponse
from fastapi.exceptions import RequestValidationError
from sqlalchemy.orm import Session
from datetime import datetime

from config import Base, db_session, engine, logger
from models import HookData as HookDataModel
from schemas import HookDataSchema, HookDataCreate

app = FastAPI(title="Sesame Hook Debug Server")

# 添加自定义异常处理器来记录详细的验证错误
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.error(f"Request validation failed: {exc.errors()}")
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"detail": exc.errors()},
    )

# 初始化数据库
Base.metadata.create_all(bind=engine)

# 依赖注入
def get_db():
    with db_session() as session:
        yield session

# 保存数据接口
@app.post("/hook", response_model=HookDataSchema)
async def create_webhook(data: HookDataCreate, db: Session = Depends(get_db)):
    try:
        validated_data = data.model_dump(exclude_unset=True)
        params_data = validated_data.get("Params")
        data_field = validated_data.get("Data")
        timestamp_data = validated_data.get("TimeStamp")

        # 序列化 Params
        if isinstance(params_data, dict):
            validated_data["Params"] = json.dumps(params_data, ensure_ascii=False)
        elif params_data is not None:
            validated_data["Params"] = str(params_data)

        # 序列化 Data
        if isinstance(data_field, dict):
            validated_data["Data"] = json.dumps(data_field, ensure_ascii=False)
        elif data_field is not None:
            validated_data["Data"] = str(data_field)

        # 格式化 TimeStamp
        if timestamp_data is not None:
            validated_data["TimeStamp"] = str(timestamp_data)

        db_data = HookDataModel(**validated_data)
        db.add(db_data)
        db.commit()
        db.refresh(db_data)
        return db_data
    except Exception as e:
        logger.error(f"Error saving data: {e}")
        raise HTTPException(status_code=500, detail="Error saving data")

# 查询数据接口 (支持按方法搜索、最新排序)
@app.get("/hook", response_model=List[HookDataSchema])
async def get_webhooks(
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    method: str = Query(None),
    db: Session = Depends(get_db),
):
    # 自动清理旧数据（每小时执行）
    if datetime.now().minute == 0:
        HookDataModel.cleanup_old_data(db, max_count=50)

    skip = (page - 1) * per_page
    query = db.query(HookDataModel)
    if method:
        query = query.filter(HookDataModel.Method.like(f"%{method}%"))

    items = (
        query.order_by(HookDataModel.id.desc())
        .offset(skip)
        .limit(per_page)
        .all()
    )
    return items

# 清空数据接口
@app.delete("/hook")
async def clear_webhooks(db: Session = Depends(get_db)):
    try:
        num_deleted = db.query(HookDataModel).delete()
        db.commit()
        return {"success": True, "message": f"Successfully deleted {num_deleted} records."}
    except Exception as e:
        logger.error(f"Error clearing data: {e}")
        db.rollback()
        raise HTTPException(status_code=500, detail="Error clearing data")

# 看板 HTML 主页
@app.get("/", response_class=HTMLResponse)
async def get_dashboard():
    html_content = """
    <!DOCTYPE html>
    <html lang="zh-CN">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Hook 数据监控看板</title>
        <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;800&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
        <style>
            :root {
                --bg-gradient: linear-gradient(135deg, #09090e 0%, #12121c 100%);
                --card-bg: rgba(26, 26, 38, 0.7);
                --card-border: rgba(255, 255, 255, 0.05);
                --text-primary: #f0f0f5;
                --text-secondary: #8f8fa3;
                --accent-color: #00ff88;
                --cyan-color: #00d9ff;
                --danger-color: #ff4a5a;
                --font-sans: 'Outfit', sans-serif;
                --font-mono: 'Fira Code', monospace;
            }

            * {
                box-sizing: border-box;
                margin: 0;
                padding: 0;
            }

            body {
                font-family: var(--font-sans);
                background: var(--bg-gradient);
                color: var(--text-primary);
                height: 100vh;
                display: flex;
                flex-direction: column;
                overflow: hidden;
            }

            /* Header Section */
            header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 1.2rem 2rem;
                background: rgba(10, 10, 16, 0.6);
                border-bottom: 1px solid var(--card-border);
                backdrop-filter: blur(10px);
            }

            .logo-area {
                display: flex;
                align-items: center;
                gap: 0.8rem;
            }

            .logo-icon {
                font-size: 1.8rem;
            }

            h1 {
                font-weight: 800;
                font-size: 1.4rem;
                letter-spacing: 0.5px;
                background: linear-gradient(90deg, #00ff88, #00d9ff);
                -webkit-background-clip: text;
                -webkit-text-fill-color: transparent;
            }

            .controls-area {
                display: flex;
                align-items: center;
                gap: 1rem;
            }

            .btn {
                font-family: var(--font-sans);
                font-weight: 600;
                padding: 0.5rem 1rem;
                border-radius: 8px;
                cursor: pointer;
                border: none;
                transition: all 0.2s ease;
                display: inline-flex;
                align-items: center;
                gap: 0.5rem;
            }

            .btn-danger {
                background: rgba(255, 74, 90, 0.15);
                color: var(--danger-color);
                border: 1px solid rgba(255, 74, 90, 0.3);
            }

            .btn-danger:hover {
                background: rgba(255, 74, 90, 0.3);
                box-shadow: 0 0 15px rgba(255, 74, 90, 0.2);
            }

            .refresh-toggle {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                font-size: 0.9rem;
                color: var(--text-secondary);
                cursor: pointer;
                background: rgba(255, 255, 255, 0.03);
                padding: 0.5rem 1rem;
                border-radius: 8px;
                border: 1px solid var(--card-border);
            }

            .refresh-toggle input {
                accent-color: var(--accent-color);
                width: 16px;
                height: 16px;
                cursor: pointer;
            }

            /* Main Layout */
            .main-content {
                display: flex;
                flex: 1;
                overflow: hidden;
            }

            /* Left Sidebar: Requests List */
            .sidebar {
                width: 38%;
                border-right: 1px solid var(--card-border);
                display: flex;
                flex-direction: column;
                background: rgba(15, 15, 24, 0.4);
            }

            .search-box {
                padding: 1rem;
                border-bottom: 1px solid var(--card-border);
            }

            .search-input {
                width: 100%;
                padding: 0.75rem 1rem;
                border-radius: 8px;
                border: 1px solid var(--card-border);
                background: rgba(0, 0, 0, 0.2);
                color: var(--text-primary);
                font-family: var(--font-sans);
                font-size: 0.95rem;
                outline: none;
                transition: border-color 0.2s ease;
            }

            .search-input:focus {
                border-color: var(--cyan-color);
                box-shadow: 0 0 10px rgba(0, 217, 255, 0.1);
            }

            .list-container {
                flex: 1;
                overflow-y: auto;
                padding: 0.5rem;
            }

            .list-item {
                padding: 1rem;
                border-radius: 8px;
                border: 1px solid transparent;
                background: rgba(255, 255, 255, 0.02);
                margin-bottom: 0.5rem;
                cursor: pointer;
                transition: all 0.2s ease;
            }

            .list-item:hover {
                background: rgba(255, 255, 255, 0.05);
                border-color: rgba(255, 255, 255, 0.08);
            }

            .list-item.active {
                background: rgba(0, 217, 255, 0.08);
                border-color: rgba(0, 217, 255, 0.3);
                box-shadow: inset 0 0 10px rgba(0, 217, 255, 0.05);
            }

            .item-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 0.4rem;
            }

            .item-id {
                font-size: 0.8rem;
                background: rgba(255, 255, 255, 0.08);
                padding: 0.1rem 0.4rem;
                border-radius: 4px;
                color: var(--text-secondary);
                font-family: var(--font-mono);
            }

            .item-time {
                font-size: 0.8rem;
                color: var(--text-secondary);
            }

            .item-method {
                font-family: var(--font-mono);
                font-size: 0.9rem;
                color: var(--cyan-color);
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            /* Right Details Section */
            .details-area {
                flex: 1;
                display: flex;
                flex-direction: column;
                padding: 1.5rem;
                overflow-y: auto;
                background: rgba(10, 10, 15, 0.2);
            }

            .placeholder-detail {
                flex: 1;
                display: flex;
                flex-direction: column;
                justify-content: center;
                align-items: center;
                color: var(--text-secondary);
                gap: 1rem;
            }

            .placeholder-icon {
                font-size: 4rem;
                opacity: 0.3;
            }

            .detail-container {
                display: none;
                flex-direction: column;
                gap: 1.5rem;
            }

            .detail-card {
                background: var(--card-bg);
                border: 1px solid var(--card-border);
                border-radius: 12px;
                padding: 1.5rem;
                box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.2);
                backdrop-filter: blur(8px);
            }

            .card-title-bar {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 1rem;
                border-bottom: 1px solid var(--card-border);
                padding-bottom: 0.6rem;
            }

            .card-title {
                font-weight: 600;
                font-size: 1.1rem;
                color: var(--text-primary);
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }

            .copy-btn {
                background: rgba(255, 255, 255, 0.05);
                border: 1px solid var(--card-border);
                color: var(--text-secondary);
                padding: 0.3rem 0.6rem;
                border-radius: 6px;
                font-size: 0.8rem;
                cursor: pointer;
                transition: all 0.2s ease;
                font-family: var(--font-sans);
            }

            .copy-btn:hover {
                background: var(--cyan-color);
                color: #000;
                border-color: var(--cyan-color);
            }

            .code-block {
                font-family: var(--font-mono);
                font-size: 0.9rem;
                line-height: 1.5;
                background: rgba(0, 0, 0, 0.3);
                padding: 1rem;
                border-radius: 8px;
                overflow-x: auto;
                max-height: 400px;
                overflow-y: auto;
                white-space: pre-wrap;
                word-break: break-all;
            }

            .method-tag {
                font-family: var(--font-mono);
                color: var(--cyan-color);
                background: rgba(0, 217, 255, 0.1);
                padding: 0.4rem 0.8rem;
                border-radius: 6px;
                border: 1px solid rgba(0, 217, 255, 0.2);
                display: inline-block;
                margin-top: 0.5rem;
                font-size: 0.95rem;
            }

            .meta-info {
                display: grid;
                grid-template-columns: repeat(2, 1fr);
                gap: 1rem;
            }

            .meta-item {
                display: flex;
                flex-direction: column;
                gap: 0.3rem;
            }

            .meta-label {
                font-size: 0.8rem;
                color: var(--text-secondary);
                text-transform: uppercase;
            }

            .meta-val {
                font-size: 0.95rem;
                font-weight: 600;
            }

            /* Scrollbars */
            ::-webkit-scrollbar {
                width: 6px;
                height: 6px;
            }

            ::-webkit-scrollbar-track {
                background: transparent;
            }

            ::-webkit-scrollbar-thumb {
                background: rgba(255, 255, 255, 0.1);
                border-radius: 3px;
            }

            ::-webkit-scrollbar-thumb:hover {
                background: rgba(255, 255, 255, 0.25);
            }
        </style>
    </head>
    <body>
        <header>
            <div class="logo-area">
                <span class="logo-icon">📡</span>
                <h1>Sesame Hook Monitor</h1>
            </div>
            <div class="controls-area">
                <label class="refresh-toggle">
                    <input type="checkbox" id="autoRefresh" checked>
                    <span>自动刷新(3s)</span>
                </label>
                <button class="btn btn-danger" onclick="clearLogs()">
                    <span>🗑️</span> 清空记录
                </button>
            </div>
        </header>

        <div class="main-content">
            <!-- Sidebar with list -->
            <div class="sidebar">
                <div class="search-box">
                    <input type="text" id="searchInput" class="search-input" placeholder="搜索方法名..." oninput="handleSearch()">
                </div>
                <div class="list-container" id="listContainer">
                    <!-- Loaded dynamically -->
                </div>
            </div>

            <!-- Details section -->
            <div class="details-area">
                <div class="placeholder-detail" id="placeholder">
                    <span class="placeholder-icon">📥</span>
                    <p>选择左侧请求以查看 RPC 参数与响应包</p>
                </div>

                <div class="detail-container" id="detailContainer">
                    <!-- Meta info card -->
                    <div class="detail-card">
                        <div class="meta-info">
                            <div class="meta-item">
                                <span class="meta-label">ID / 序列号</span>
                                <span class="meta-val" id="detId">-</span>
                            </div>
                            <div class="meta-item">
                                <span class="meta-label">捕获时间</span>
                                <span class="meta-val" id="detTime">-</span>
                            </div>
                        </div>
                        <div style="margin-top: 1rem;">
                            <span class="meta-label">RPC 方法名 (Method)</span>
                            <div>
                                <span class="method-tag" id="detMethod">-</span>
                            </div>
                        </div>
                    </div>

                    <!-- Params card -->
                    <div class="detail-card">
                        <div class="card-title-bar">
                            <span class="card-title">📦 请求参数 (Params)</span>
                            <button class="copy-btn" onclick="copyText('detParams')">复制 JSON</button>
                        </div>
                        <pre class="code-block" id="detParams"></pre>
                    </div>

                    <!-- Response data card -->
                    <div class="detail-card">
                        <div class="card-title-bar">
                            <span class="card-title">📤 响应数据 (Data)</span>
                            <button class="copy-btn" onclick="copyText('detData')">复制 JSON</button>
                        </div>
                        <pre class="code-block" id="detData"></pre>
                    </div>
                </div>
            </div>
        </div>

        <script>
            let currentLogs = [];
            let activeId = null;
            let refreshInterval = null;

            async function fetchLogs() {
                try {
                    const searchVal = document.getElementById('searchInput').value;
                    let url = '/hook?per_page=50';
                    if (searchVal) {
                        url += `&method=${encodeURIComponent(searchVal)}`;
                    }

                    const response = await fetch(url);
                    const data = await response.json();
                    currentLogs = data;
                    renderList();
                } catch (e) {
                    console.error("Error fetching webhooks:", e);
                }
            }

            function renderList() {
                const container = document.getElementById('listContainer');
                
                if (currentLogs.length === 0) {
                    container.innerHTML = '<div class="placeholder-detail" style="height: 100px;"><p>暂无数据记录</p></div>';
                    return;
                }

                let html = '';
                currentLogs.forEach(item => {
                    const isActive = activeId === item.id;
                    const dateStr = item.created_at ? formatTime(item.created_at) : (item.TimeStamp ? formatTimestamp(item.TimeStamp) : '-');
                    
                    html += `
                        <div class="list-item ${isActive ? 'active' : ''}" onclick="selectItem(${item.id})">
                            <div class="item-header">
                                <span class="item-id">#${item.id}</span>
                                <span class="item-time">${dateStr}</span>
                            </div>
                            <div class="item-method" title="${item.Method}">${item.Method}</div>
                        </div>
                    `;
                });
                container.innerHTML = html;
            }

            function selectItem(id) {
                activeId = id;
                renderList(); // 更新高亮

                const item = currentLogs.find(x => x.id === id);
                if (!item) return;

                document.getElementById('placeholder').style.display = 'none';
                document.getElementById('detailContainer').style.display = 'flex';

                document.getElementById('detId').innerText = `#${item.id}`;
                document.getElementById('detTime').innerText = item.created_at ? formatTime(item.created_at) : (item.TimeStamp ? formatTimestamp(item.TimeStamp) : '-');
                document.getElementById('detMethod').innerText = item.Method;

                // 格式化 Params
                let formattedParams = item.Params;
                try {
                    const parsed = JSON.parse(item.Params);
                    formattedParams = JSON.stringify(parsed, null, 2);
                } catch (e) {
                    // Fallback to raw
                }
                document.getElementById('detParams').innerText = formattedParams;

                // 格式化 Data
                let formattedData = item.Data;
                try {
                    const parsed = JSON.parse(item.Data);
                    formattedData = JSON.stringify(parsed, null, 2);
                } catch (e) {
                    // Fallback to raw
                }
                document.getElementById('detData').innerText = formattedData;
            }

            async function clearLogs() {
                if (!confirm("确定要清空数据库中的所有 Hook 数据记录吗？")) return;
                try {
                    const response = await fetch('/hook', { method: 'DELETE' });
                    const res = await response.json();
                    if (res.success) {
                        activeId = null;
                        document.getElementById('placeholder').style.display = 'flex';
                        document.getElementById('detailContainer').style.display = 'none';
                        fetchLogs();
                    }
                } catch (e) {
                    alert("清空记录失败: " + e.message);
                }
            }

            function handleSearch() {
                fetchLogs();
            }

            function copyText(elementId) {
                const text = document.getElementById(elementId).innerText;
                navigator.clipboard.writeText(text).then(() => {
                    alert("已复制到剪贴板！");
                }).catch(err => {
                    alert("复制失败，请手动选择复制。");
                });
            }

            function formatTime(isoStr) {
                try {
                    const d = new Date(isoStr);
                    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
                } catch (e) {
                    return isoStr;
                }
            }

            function formatTimestamp(tsStr) {
                try {
                    const d = new Date(parseInt(tsStr));
                    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
                } catch (e) {
                    return tsStr;
                }
            }

            // Polling Setup
            function setupPolling() {
                const autoRefresh = document.getElementById('autoRefresh').checked;
                if (refreshInterval) clearInterval(refreshInterval);
                
                if (autoRefresh) {
                    refreshInterval = setInterval(fetchLogs, 3000);
                }
            }

            document.getElementById('autoRefresh').addEventListener('change', setupPolling);

            // Initial load
            fetchLogs();
            setupPolling();
        </script>
    </body>
    </html>
    """
    return html_content

if __name__ == "__main__":
    import uvicorn
    logger.info("Starting FastAPI server...")
    uvicorn.run(app, host="0.0.0.0", port=9527)
    logger.info("FastAPI server stopped.")
