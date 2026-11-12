<template>
  <div>
    <div class="card filters">
      <div class="filter-row">
        <div class="filter-item" style="align-items: flex-start">
          <label>服务</label>
          <div class="chips">
            <button
              v-for="service in services"
              :key="service.appName"
              class="chip"
              :class="{ active: selectedApps.has(service.appName) }"
              @click="toggleService(service.appName)"
            >
              {{ service.appName }}
            </button>
            <span v-if="!services.length" class="hint">暂无服务</span>
          </div>
        </div>
        <div class="filter-item">
          <label>类型</label>
          <select v-model="query.type">
            <option value="">全部</option>
            <option v-for="type in alertTypes" :key="type" :value="type">{{ type }}</option>
          </select>
        </div>
        <div class="filter-item">
          <label>级别</label>
          <select v-model="query.level">
            <option value="">全部</option>
            <option v-for="level in alertLevels" :key="level" :value="level">{{ level }}</option>
          </select>
        </div>
        <div class="filter-item">
          <label>时间</label>
          <select v-model="query.range">
            <option value="1h">最近 1 小时</option>
            <option value="6h">最近 6 小时</option>
            <option value="24h">最近 24 小时</option>
            <option value="7d">最近 7 天</option>
          </select>
        </div>
        <div class="filter-item">
          <label>关键字</label>
          <input
            v-model="query.keyword"
            type="text"
            placeholder="标题 / URI / traceId"
            @keyup.enter="search"
          />
        </div>
        <button class="btn primary small" @click="search">查询</button>
        <button class="btn small" @click="reset">重置</button>
      </div>
    </div>

    <div class="stats">
      <div class="stat">
        <div class="stat-label">告警总数</div>
        <div class="stat-value">{{ page.total }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">严重</div>
        <div class="stat-value" style="color: #b91c1c">{{ page.critical }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">错误</div>
        <div class="stat-value" style="color: #b45309">{{ page.error }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">警告</div>
        <div class="stat-value" style="color: #1d4ed8">{{ page.warn }}</div>
      </div>
    </div>

    <div class="card" v-loading="loading">
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th style="width: 150px">时间</th>
              <th style="width: 120px">服务</th>
              <th style="width: 140px">类型</th>
              <th style="width: 90px">级别</th>
              <th>标题</th>
              <th style="width: 180px">URI</th>
              <th style="width: 80px">耗时</th>
              <th style="width: 90px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="alert in page.items" :key="alert.id">
              <td class="mono">{{ formatTime(alert.alertTime) }}</td>
              <td><strong>{{ alert.appName }}</strong></td>
              <td><span class="badge" :class="typeClass(alert.alertType)">{{ alert.alertType }}</span></td>
              <td><span class="badge" :class="levelClass(alert.alertLevel)">{{ alert.alertLevel }}</span></td>
              <td>
                <div class="alert-title">{{ alert.title }}</div>
                <div class="alert-sub">
                  traceId: {{ alert.traceId || '-' }} · 重复 {{ alert.repeatCount }} 次
                </div>
              </td>
              <td class="mono">{{ alert.requestUri || '-' }}</td>
              <td class="mono">{{ alert.costMs == null ? '-' : alert.costMs + 'ms' }}</td>
              <td><a class="link" @click="openAlert(alert.id)">详情</a></td>
            </tr>
            <tr v-if="!page.items.length">
              <td colspan="8" class="empty">没有匹配的告警</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pager">
        <span class="hint">共 {{ page.total }} 条 · 第 {{ page.page }} / {{ Math.max(1, page.totalPages) }} 页</span>
        <button class="btn small" :disabled="page.page <= 1" @click="go(page.page - 1)">上一页</button>
        <button class="btn small" :disabled="page.page >= page.totalPages" @click="go(page.page + 1)">下一页</button>
      </div>
    </div>

    <AlertDetailDrawer v-model="drawerVisible" :alert-id="selectedAlertId" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listServices, queryAlerts } from '../api'
import AlertDetailDrawer from '../components/AlertDetailDrawer.vue'

const alertTypes = ['BIZ_EXCEPTION', 'SLOW_REQUEST', 'NULL_POINTER', 'STACK_OVERFLOW', 'HTTP_OUTBOUND']
const alertLevels = ['CRITICAL', 'ERROR', 'WARN', 'INFO']
const PAGE_SIZE = 20

const services = ref([])
const selectedApps = ref(new Set())
const appsInitialized = ref(false)
const query = reactive({ type: '', level: '', range: '24h', keyword: '' })
const page = ref(emptyPage())
const loading = ref(false)
const drawerVisible = ref(false)
const selectedAlertId = ref(null)

function emptyPage() {
  return { items: [], total: 0, page: 1, size: PAGE_SIZE, totalPages: 0, critical: 0, error: 0, warn: 0 }
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ')
}

function typeClass(type) {
  const map = {
    BIZ_EXCEPTION: 'blue',
    SLOW_REQUEST: 'orange',
    NULL_POINTER: 'red',
    STACK_OVERFLOW: 'purple',
    HTTP_OUTBOUND: 'gray'
  }
  return map[type] || 'gray'
}

function levelClass(level) {
  const map = { CRITICAL: 'red', ERROR: 'red', WARN: 'orange', INFO: 'gray' }
  return map[level] || 'gray'
}

function toggleService(appName) {
  const next = new Set(selectedApps.value)
  if (next.has(appName)) {
    next.delete(appName)
  } else {
    next.add(appName)
  }
  selectedApps.value = next
  search()
}

async function fetchPage(pageNo) {
  loading.value = true
  try {
    const appNames = selectedApps.value.size === services.value.length
      ? ''
      : Array.from(selectedApps.value).join(',')
    page.value = await queryAlerts({
      appNames,
      type: query.type,
      level: query.level,
      range: query.range,
      keyword: query.keyword,
      page: pageNo,
      size: PAGE_SIZE
    })
  } catch (e) {
    ElMessage.error(e.message || '查询告警失败')
  } finally {
    loading.value = false
  }
}

function search() {
  fetchPage(1)
}

function go(pageNo) {
  if (pageNo < 1 || pageNo > page.value.totalPages) {
    return
  }
  fetchPage(pageNo)
}

function reset() {
  selectedApps.value = new Set(services.value.map(item => item.appName))
  query.type = ''
  query.level = ''
  query.range = '24h'
  query.keyword = ''
  fetchPage(1)
}

function openAlert(id) {
  selectedAlertId.value = id
  drawerVisible.value = true
}

async function reload() {
  try {
    services.value = (await listServices()) || []
    if (!appsInitialized.value) {
      selectedApps.value = new Set(services.value.map(item => item.appName))
      appsInitialized.value = true
    }
    await fetchPage(page.value.page || 1)
  } catch (e) {
    ElMessage.error(e.message || '加载服务列表失败')
  }
}

onMounted(reload)

defineExpose({ refresh: reload })
</script>
