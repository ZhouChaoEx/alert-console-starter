<template>
  <div class="dash">
    <div class="dash-head">
      <div>
        <div class="dash-title">告警监控大屏</div>
        <div class="dash-sub">
          PROD · 项目组共享库 · {{ serviceCount }} 个服务 · 更新于 {{ displayTime }}
        </div>
      </div>
      <div class="dash-actions">
        <div class="dash-range">
          <button
            v-for="item in ranges"
            :key="item.value"
            class="dash-range-btn"
            :class="{ active: range === item.value }"
            @click="setRange(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
        <span class="dash-time">{{ clock }}</span>
        <button class="dash-btn" :disabled="loading" @click="load">刷新</button>
        <button class="dash-btn" @click="cycleAutoRefresh">{{ autoLabel }}</button>
        <button class="dash-btn" @click="toggleFullscreen">全屏</button>
      </div>
    </div>

    <div class="dash-kpis">
      <div v-for="kpi in kpis" :key="kpi.label" class="dash-kpi">
        <div class="label">{{ kpi.label }}</div>
        <div class="value" :style="kpi.color ? { color: kpi.color } : null">{{ kpi.value }}</div>
      </div>
    </div>

    <div class="dash-grid">
      <div class="dash-card span-2">
        <div class="dash-card-title">
          <span>{{ rangeLabel }}告警趋势</span><span>按级别堆叠</span>
        </div>
        <div class="trend-chart">
          <div
            v-for="point in data.trend"
            :key="point.bucket"
            class="trend-col"
            :title="`${point.bucket} 共 ${point.total} 条`"
          >
            <div
              v-if="segmentHeight(point, 'critical')"
              class="seg critical"
              :style="{ height: segmentHeight(point, 'critical') + 'px' }"
            ></div>
            <div
              v-if="segmentHeight(point, 'error')"
              class="seg error"
              :style="{ height: segmentHeight(point, 'error') + 'px' }"
            ></div>
            <div
              v-if="segmentHeight(point, 'warn')"
              class="seg warn"
              :style="{ height: segmentHeight(point, 'warn') + 'px' }"
            ></div>
          </div>
          <div v-if="!data.trend.length" class="empty">该时间范围没有告警</div>
        </div>
        <div class="trend-legend">
          <span><i class="dot critical"></i>CRITICAL</span>
          <span><i class="dot error"></i>ERROR</span>
          <span><i class="dot warn"></i>WARN</span>
        </div>
      </div>

      <div class="dash-card">
        <div class="dash-card-title">告警类型分布</div>
        <div class="type-list">
          <div v-for="item in data.types" :key="item.type" class="type-row">
            <span class="type-name" :title="item.type">{{ item.type }}</span>
            <div class="type-track">
              <i :style="{ width: percent(item.count, typeMax) + '%', background: typeColor(item.type) }"></i>
            </div>
            <span class="type-count">{{ item.count }}</span>
          </div>
          <div v-if="!data.types.length" class="empty">暂无数据</div>
        </div>
      </div>

      <div class="dash-card">
        <div class="dash-card-title">服务告警 Top 10</div>
        <div class="bar-list">
          <div v-for="item in data.services" :key="item.appName" class="bar-row">
            <span class="bar-name" :title="item.appName">{{ item.appName }}</span>
            <div class="bar-track">
              <i :style="{ width: percent(item.total, serviceMax) + '%', background: item.critical ? '#ef4444' : '#3b82f6' }"></i>
            </div>
            <span class="bar-value">{{ item.total }}</span>
          </div>
          <div v-if="!data.services.length" class="empty">暂无数据</div>
        </div>
      </div>

      <div class="dash-card">
        <div class="dash-card-title">实时告警流 <span>最近 8 条</span></div>
        <div class="latest-list">
          <div
            v-for="alert in data.latest.slice(0, 8)"
            :key="alert.id"
            class="latest-item"
            @click="openAlert(alert.id)"
          >
            <span class="latest-time">{{ shortTime(alert.alertTime) }}</span>
            <span class="latest-main">
              <div class="latest-title">{{ alert.title }}</div>
              <div class="latest-meta">{{ alert.appName }} · {{ alert.alertType }} · {{ alert.alertLevel }}</div>
            </span>
          </div>
          <div v-if="!data.latest.length" class="empty">暂无告警</div>
        </div>
      </div>

      <div class="dash-card">
        <div class="dash-card-title">服务健康矩阵</div>
        <div class="health-grid">
          <div v-for="item in healthItems" :key="item.name" class="health-item" :title="`${item.instances} 个实例`">
            <span>{{ item.name }}</span>
            <b :style="{ color: healthColor(item.count) }">{{ item.count }}</b>
          </div>
          <div v-if="!healthItems.length" class="empty">暂无服务</div>
        </div>
      </div>

      <div class="dash-card">
        <div class="dash-card-title">慢请求 Top URI</div>
        <div class="bar-list">
          <div v-for="item in data.slowRequests" :key="item.uri" class="bar-row">
            <span class="bar-name" :title="item.uri">{{ item.uri }}</span>
            <div class="bar-track">
              <i :style="{ width: percent(item.maxCostMs, slowMax) + '%', background: '#f59e0b' }"></i>
            </div>
            <span class="bar-value">{{ item.maxCostMs }}ms</span>
          </div>
          <div v-if="!data.slowRequests.length" class="empty">暂无数据</div>
        </div>
      </div>

      <div class="dash-card">
        <div class="dash-card-title">资源水位 <span>M2/M3 预留</span></div>
        <div v-if="data.resources.available" class="resource-list">
          <div class="hint">探针已接入</div>
        </div>
        <div v-else class="hint">{{ data.resources.message || '资源探针尚未实现' }}</div>
      </div>
    </div>

    <AlertDetailDrawer v-model="drawerVisible" :alert-id="selectedAlertId" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboard, listServices } from '../api'
import AlertDetailDrawer from '../components/AlertDetailDrawer.vue'

const ranges = [
  { value: '1h', label: '1h' },
  { value: '6h', label: '6h' },
  { value: '24h', label: '24h' },
  { value: '7d', label: '7d' }
]
const RANGE_LABELS = { '1h': '1 小时', '6h': '6 小时', '24h': '24 小时', '7d': '7 天' }

const range = ref('24h')
const data = ref(emptyDashboard())
const services = ref([])
const clock = ref(formatClock())
const autoSeconds = ref(30)
const loading = ref(false)
const drawerVisible = ref(false)
const selectedAlertId = ref(null)

let autoTimer = null
let clockTimer = null

const displayTime = computed(() => data.value.generatedAt || '-')
const rangeLabel = computed(() => RANGE_LABELS[range.value] || '24 小时')
const serviceCount = computed(() => services.value.length)
const autoLabel = computed(() => (autoSeconds.value ? `自动刷新 ${autoSeconds.value}s` : '自动刷新：关闭'))

const kpis = computed(() => {
  const summary = data.value.summary
  return [
    { label: '今日告警', value: summary.total },
    { label: '近 1 小时', value: summary.lastHour },
    { label: '严重', value: summary.critical, color: '#f87171' },
    { label: '错误', value: summary.error, color: '#fbbf24' },
    { label: '警告', value: summary.warn, color: '#60a5fa' },
    { label: '受影响服务', value: summary.affectedServices, color: '#a78bfa' }
  ]
})

const typeMax = computed(() => Math.max(...data.value.types.map(item => item.count), 1))
const serviceMax = computed(() => Math.max(...data.value.services.map(item => item.total), 1))
const slowMax = computed(() => Math.max(...data.value.slowRequests.map(item => item.maxCostMs), 1))
const trendMax = computed(() =>
  Math.max(...data.value.trend.map(point => point.total), 1)
)

const healthItems = computed(() => {
  const counts = new Map(data.value.services.map(item => [item.appName, item.total]))
  if (!services.value.length) {
    return data.value.services.map(item => ({
      name: item.appName,
      count: item.total,
      instances: '-'
    }))
  }
  return services.value.map(service => ({
    name: service.appName,
    count: counts.get(service.appName) || 0,
    instances: service.instanceCount
  }))
})

function emptyDashboard() {
  return {
    range: '24h',
    generatedAt: '',
    summary: { total: 0, critical: 0, error: 0, warn: 0, affectedServices: 0, lastHour: 0, avgCostMs: 0 },
    trend: [],
    types: [],
    services: [],
    latest: [],
    slowRequests: [],
    resources: { available: false, message: '' }
  }
}

function formatClock() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function formatDateTime(value) {
  if (!value) {
    return ''
  }
  return String(value).replace('T', ' ')
}

function shortTime(value) {
  const text = formatDateTime(value)
  return text.length >= 16 ? text.substring(11, 16) : text
}

function percent(value, max) {
  return Math.min(100, Math.round((value || 0) / (max || 1) * 100))
}

function typeColor(type) {
  const map = {
    SLOW_REQUEST: '#3b82f6',
    BIZ_EXCEPTION: '#8b5cf6',
    NULL_POINTER: '#ef4444',
    STACK_OVERFLOW: '#f59e0b',
    HTTP_OUTBOUND: '#10b981'
  }
  return map[type] || '#3b82f6'
}

function healthColor(count) {
  if (count >= 3) {
    return '#f87171'
  }
  return count > 0 ? '#fbbf24' : '#86efac'
}

function barHeight(point) {
  return Math.max(2, Math.round((point.total || 0) / trendMax.value * 140))
}

function segmentHeight(point, level) {
  const total = point.total || 0
  const height = barHeight(point)
  if (!total) {
    return level === 'warn' ? height : 0
  }
  const criticalHeight = Math.round(point.critical / total * height)
  const errorHeight = Math.round(point.error / total * height)
  if (level === 'critical') {
    return criticalHeight
  }
  if (level === 'error') {
    return errorHeight
  }
  return Math.max(0, height - criticalHeight - errorHeight)
}

async function load() {
  loading.value = true
  try {
    const [dashboard, serviceList] = await Promise.all([
      getDashboard(range.value),
      listServices()
    ])
    data.value = dashboard || emptyDashboard()
    services.value = serviceList || []
    clock.value = formatClock()
  } catch (e) {
    ElMessage.error(e.message || '加载大屏数据失败')
  } finally {
    loading.value = false
  }
}

function setRange(value) {
  if (range.value === value) {
    return
  }
  range.value = value
  load()
}

function cycleAutoRefresh() {
  if (autoSeconds.value === 30) {
    autoSeconds.value = 60
  } else if (autoSeconds.value === 60) {
    autoSeconds.value = 0
  } else {
    autoSeconds.value = 30
  }
  scheduleAutoRefresh()
}

function scheduleAutoRefresh() {
  if (autoTimer) {
    clearInterval(autoTimer)
    autoTimer = null
  }
  if (autoSeconds.value > 0) {
    autoTimer = setInterval(load, autoSeconds.value * 1000)
  }
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    if (document.documentElement.requestFullscreen) {
      document.documentElement.requestFullscreen()
    }
  } else if (document.exitFullscreen) {
    document.exitFullscreen()
  }
}

function openAlert(id) {
  selectedAlertId.value = id
  drawerVisible.value = true
}

onMounted(() => {
  load()
  scheduleAutoRefresh()
  clockTimer = setInterval(() => {
    clock.value = formatClock()
  }, 1000)
})

onUnmounted(() => {
  if (autoTimer) {
    clearInterval(autoTimer)
  }
  if (clockTimer) {
    clearInterval(clockTimer)
  }
})

defineExpose({ refresh: load })
</script>
