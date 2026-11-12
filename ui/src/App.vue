<template>
  <div class="app">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-icon">A</div>
        <div class="brand-text">
          <div class="brand-title">告警管理</div>
          <div class="brand-sub">Alert Console</div>
        </div>
      </div>
      <nav class="nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: page === item.key }"
          @click="page = item.key"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </button>
      </nav>
      <div class="sidebar-foot">
        当前环境<br /><strong>PROD · 项目组共享库</strong><br />{{ serviceCount }} 个服务
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <h1>{{ pageMeta.title }}</h1>
          <p>{{ pageMeta.subtitle }}</p>
        </div>
        <div class="top-actions">
          <span class="env-tag">共享库 · {{ serviceCount }} 个服务</span>
          <button class="btn ghost" @click="refreshCurrent">刷新数据</button>
        </div>
      </header>

      <Dashboard v-if="page === 'dashboard'" ref="dashboardRef" />
      <ConfigPage v-else-if="page === 'config'" ref="configRef" />
      <AlertList v-else ref="alertListRef" />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { listServices } from './api'
import Dashboard from './views/Dashboard.vue'
import ConfigPage from './views/ConfigPage.vue'
import AlertList from './views/AlertList.vue'

const navItems = [
  { key: 'dashboard', label: '告警大屏', icon: '▦' },
  { key: 'config', label: '告警配置', icon: '⚙' },
  { key: 'alerts', label: '告警列表', icon: '☰' }
]

const page = ref('config')
const serviceCount = ref(0)
const dashboardRef = ref(null)
const configRef = ref(null)
const alertListRef = ref(null)

const pageMeta = computed(() => {
  if (page.value === 'dashboard') {
    return { title: '告警大屏', subtitle: '跨服务告警态势、趋势与实时告警流' }
  }
  if (page.value === 'alerts') {
    return { title: '告警列表', subtitle: '跨服务查询 alert_info 告警内容' }
  }
  return { title: '告警配置', subtitle: '按服务查看和调整告警参数，保存后可刷新指定服务' }
})

async function loadServiceCount() {
  try {
    serviceCount.value = (await listServices()).length
  } catch (e) {
    serviceCount.value = 0
  }
}

async function refreshCurrent() {
  const target = page.value === 'dashboard'
    ? dashboardRef.value
    : page.value === 'config'
      ? configRef.value
      : alertListRef.value
  if (target && target.refresh) {
    await target.refresh()
  }
  await loadServiceCount()
}

onMounted(loadServiceCount)
</script>
