<template>
  <div>
    <div class="card toolbar">
      <div class="toolbar-left">
        <label>当前服务</label>
        <select :value="currentApp" @change="selectService($event.target.value)">
          <option v-for="service in services" :key="service.appName" :value="service.appName">
            {{ service.appName }}
          </option>
        </select>
        <span class="hint">{{ currentServiceMeta }}</span>
        <label>查看实例</label>
        <select
          :value="selectedInstance"
          :disabled="effectiveLoading"
          @change="selectInstance($event.target.value)"
        >
          <option v-for="item in instanceOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <span v-if="remoteMeta" class="hint" :title="remoteMeta">{{ remoteMeta }}</span>
        <button class="btn small" :disabled="creating" @click="createService">
          {{ creating ? '创建中…' : '+ 新增服务' }}
        </button>
      </div>
      <div class="toolbar-right">
        <button class="btn" :disabled="saving || !dirtyCount" @click="save(false)">
          保存{{ dirtyCount ? ` (${dirtyCount})` : '' }}
        </button>
        <button class="btn primary" :disabled="saving || !dirtyCount" @click="save(true)">保存并刷新</button>
        <button class="btn ghost" :disabled="refreshing || !currentApp" @click="refresh">只刷新该服务</button>
      </div>
    </div>

    <div class="config-layout">
      <aside class="card service-list">
        <div class="card-title">服务列表 <span class="hint">{{ services.length }} 个</span></div>
        <div
          v-for="service in services"
          :key="service.appName"
          class="service-item"
          :class="{ active: service.appName === currentApp }"
          @click="selectService(service.appName)"
        >
          <div class="service-name">
            {{ service.appName }}
            <span class="badge" :class="service.status === 'ok' ? 'green' : 'orange'">
              {{ service.status === 'ok' ? '正常' : '待关注' }}
            </span>
          </div>
          <div class="service-meta">
            <span>v{{ service.version }}</span>
            <span>{{ service.instanceCount }} 实例</span>
            <span>{{ service.alertCount }} 告警</span>
          </div>
        </div>
        <div v-if="!services.length" class="empty">暂无服务</div>
      </aside>

      <div class="config-main">
        <div class="card config-card">
          <div class="config-head">
            <div>
              <h2>{{ config.appName || '-' }}</h2>
              <p>
                配置版本 {{ config.version }} · {{ config.instances.length }} 个实例
                <span v-if="!config.currentApp"> · 非当前应用，实例生效值不可见</span>
              </p>
            </div>
            <div>
              <span class="badge" :class="config.initialized ? 'green' : 'orange'">
                {{ config.initialized ? '已初始化' : '未初始化' }}
              </span>
            </div>
          </div>
          <div class="group-tabs">
            <button
              v-for="group in groups"
              :key="group.key"
              class="group-tab"
              :class="{ active: activeGroup === group.key }"
              @click="activeGroup = group.key"
            >
              {{ group.label }}
            </button>
          </div>
          <div class="table-wrap">
            <table class="table config-table">
              <thead>
                <tr>
                  <th>配置项</th>
                  <th style="width: 280px">值</th>
                  <th>当前实例生效值</th>
                  <th>类型</th>
                  <th>标记</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in visibleItems" :key="item.key" :class="{ 'config-dirty': isDirty(item) }">
                  <td>
                    <div class="config-key">{{ item.key }}</div>
                    <div class="config-desc">{{ item.description }}</div>
                  </td>
                  <td>
                    <select
                      v-if="item.type === 'BOOL'"
                      class="config-select"
                      :value="item.value"
                      :disabled="!item.editable"
                      @change="item.value = $event.target.value"
                    >
                      <option value="true">true</option>
                      <option value="false">false</option>
                    </select>
                    <textarea
                      v-else-if="item.type === 'LIST'"
                      class="config-input"
                      rows="2"
                      :value="item.value"
                      :disabled="!item.editable"
                      @input="item.value = $event.target.value"
                    ></textarea>
                    <input
                      v-else
                      class="config-input"
                      :value="item.value"
                      :disabled="!item.editable"
                      @input="item.value = $event.target.value"
                    />
                  </td>
                  <td class="mono" :title="selectedInstance && remoteMeta ? remoteMeta : ''">
                    {{ effectiveValue(item) }}
                  </td>
                  <td><span class="badge gray">{{ item.type }}</span></td>
                  <td>
                    <span v-if="isDirty(item)" class="badge blue">已修改</span>
                    <span v-if="item.requiresRestart" class="badge orange">需重启</span>
                    <span v-if="item.missing" class="badge gray">未初始化</span>
                    <span v-if="!item.editable" class="badge gray">只读</span>
                    <span v-if="!isDirty(item) && !item.requiresRestart && !item.missing && item.editable" class="hint">—</span>
                  </td>
                </tr>
                <tr v-if="!visibleItems.length">
                  <td colspan="5" class="empty">当前分组没有配置项</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="card refresh-card">
          <div class="card-title">
            刷新结果
            <span v-if="refreshSummary" class="hint">
              {{ refreshSummary.appName }} · 成功 {{ refreshSummary.success }} / {{ refreshSummary.total }}
            </span>
          </div>
          <div v-if="refreshSummary && refreshSummary.results.length">
            <div v-for="(item, index) in refreshSummary.results" :key="index" class="refresh-item">
              <div>
                <div class="mono">{{ item.instance }}</div>
                <div class="hint">{{ item.message }}</div>
              </div>
              <div style="text-align: right">
                <span class="badge" :class="item.success ? 'green' : 'red'">{{ item.success ? '成功' : '失败' }}</span>
                <div class="hint">{{ item.costMs }}ms</div>
              </div>
            </div>
          </div>
          <div v-else class="empty">保存并刷新后展示每个实例的结果</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createApp, getConfig, getEffective, listServices, refreshService, saveConfig } from '../api'

const groups = [
  { key: 'ALL', label: '全部' },
  { key: 'CONSOLE', label: '控制台' },
  { key: 'WRITE', label: '写入' },
  { key: 'DEDUP', label: '去重' },
  { key: 'RATE_LIMIT', label: '限流' },
  { key: 'BIZ', label: '业务异常' },
  { key: 'ERROR', label: '异常/堆栈' },
  { key: 'SLOW_REQUEST', label: '慢请求' },
  { key: 'ASYNC', label: '异步异常' },
  { key: 'SLOW_SQL', label: '慢 SQL' },
  { key: 'MEMORY', label: '内存' },
  { key: 'HTTP_OUTBOUND', label: '出站 HTTP' }
]

const services = ref([])
const currentApp = ref('')
const config = ref(emptyConfig())
const activeGroup = ref('ALL')
const saving = ref(false)
const refreshing = ref(false)
const refreshSummary = ref(null)
const creating = ref(false)
const selectedInstance = ref('')
const remoteEffective = ref(null)
const effectiveLoading = ref(false)

const instanceOptions = computed(() => {
  const options = [{ value: '', label: '本机（当前进程）' }]
  const instances = config.value.instances || []
  instances.forEach(item => options.push({ value: item, label: item }))
  return options
})

const remoteMeta = computed(() => {
  if (!selectedInstance.value || !remoteEffective.value) {
    return ''
  }
  const remote = remoteEffective.value
  if (!remote.reachable) {
    return remote.message || '不可达'
  }
  return `v${remote.version}${remote.loadedAt ? ' · ' + remote.loadedAt : ''}`
})

const currentServiceMeta = computed(() => {
  const service = services.value.find(item => item.appName === currentApp.value)
  if (!service) {
    return ''
  }
  return `${service.instanceCount} 个实例 · 版本 ${service.version} · ${service.alertCount} 条告警`
})

const visibleItems = computed(() =>
  config.value.items.filter(item => activeGroup.value === 'ALL' || item.group === activeGroup.value)
)

const dirtyItems = computed(() => config.value.items.filter(isDirty))
const dirtyCount = computed(() => dirtyItems.value.length)

function emptyConfig() {
  return {
    appName: '',
    initialized: false,
    version: 0,
    currentApp: false,
    items: [],
    instances: []
  }
}

function isDirty(item) {
  return item.value !== item.originalValue
}

function applyConfig(view, options = {}) {
  const { resetGroup = true, clearRefresh = true } = options
  const next = view || emptyConfig()
  next.items = (next.items || []).map(item => Object.assign({}, item, { originalValue: item.value }))
  config.value = next
  if (resetGroup) {
    activeGroup.value = 'ALL'
  }
  if (clearRefresh) {
    refreshSummary.value = null
  }
}

async function loadServices(preserve = true) {
  const list = await listServices()
  services.value = list || []
  if (!preserve || !currentApp.value) {
    const current = services.value.find(item => item.currentApp) || services.value[0]
    currentApp.value = current ? current.appName : ''
  }
}

async function loadConfig(appName, options) {
  if (!appName) {
    applyConfig(emptyConfig(), options)
    return
  }
  try {
    applyConfig(await getConfig(appName), options)
  } catch (e) {
    applyConfig(emptyConfig(), options)
    ElMessage.error(e.message || '加载配置失败')
  }
}

async function selectService(appName) {
  if (!appName || appName === currentApp.value) {
    return
  }
  if (dirtyCount.value) {
    ElMessage.warning('当前服务有未保存的修改，切换后不会保留')
  }
  currentApp.value = appName
  selectedInstance.value = ''
  remoteEffective.value = null
  await loadConfig(appName)
}

async function save(andRefresh) {
  if (!dirtyCount.value) {
    return
  }
  saving.value = true
  try {
    const payload = {
      appName: config.value.appName,
      version: config.value.version,
      items: dirtyItems.value.map(item => ({
        key: item.key,
        value: item.value,
        version: item.version
      }))
    }
    const previousApp = config.value.appName
    applyConfig(await saveConfig(payload), { resetGroup: false })
    currentApp.value = previousApp
    ElMessage.success(`已保存 ${previousApp} 配置，共 ${payload.items.length} 项`)
    await loadServices(true)
    if (andRefresh) {
      await refresh()
    }
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function refresh() {
  if (!currentApp.value) {
    return
  }
  refreshing.value = true
  try {
    const summary = await refreshService(currentApp.value)
    refreshSummary.value = summary
    if (summary.failed) {
      ElMessage.warning(`${summary.appName} 刷新完成：成功 ${summary.success}，失败 ${summary.failed}`)
    } else {
      ElMessage.success(`${summary.appName} 刷新完成：${summary.success} 个实例全部成功`)
    }
    // 刷新后重新拉取配置，"当前实例生效值"才会显示最新值
    await loadConfig(currentApp.value, { resetGroup: false, clearRefresh: false })
    if (selectedInstance.value) {
      await loadRemoteEffective(selectedInstance.value)
    }
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

/**
 * 表格"当前实例生效值"列：选中本机时用配置接口返回值，选中远程实例时用远程拉取结果。
 */
function effectiveValue(item) {
  if (selectedInstance.value) {
    const remote = remoteEffective.value
    if (!remote) {
      return '加载中…'
    }
    if (!remote.reachable) {
      return '不可达'
    }
    const value = (remote.effective || {})[item.key]
    return value == null ? '-' : value
  }
  return item.effectiveValue == null
    ? (config.currentApp ? '-' : '不可见')
    : item.effectiveValue
}

async function selectInstance(instance) {
  const value = instance || ''
  selectedInstance.value = value
  if (!value) {
    remoteEffective.value = null
    return
  }
  await loadRemoteEffective(value)
}

async function loadRemoteEffective(instance) {
  if (!instance || !currentApp.value) {
    remoteEffective.value = null
    return
  }
  effectiveLoading.value = true
  try {
    remoteEffective.value = await getEffective(currentApp.value, instance)
  } catch (e) {
    remoteEffective.value = { reachable: false, message: e.message || '拉取失败' }
  } finally {
    effectiveLoading.value = false
  }
}

/**
 * 新增服务：调后端复制全部默认配置项（与 alert_config.sql 的 INSERT 一致）。
 */
async function createService() {
  let appName
  try {
    const result = await ElMessageBox.prompt(
      '服务名需与应用的 spring.application.name 一致；创建后会复制全部默认配置项。',
      '新增服务',
      {
        confirmButtonText: '创建',
        cancelButtonText: '取消',
        inputPattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/,
        inputErrorMessage: '只能包含字母、数字、点、下划线、短横线，且以字母或数字开头'
      }
    )
    appName = (result.value || '').trim()
  } catch (e) {
    return
  }
  if (!appName) {
    ElMessage.warning('服务名不能为空')
    return
  }
  creating.value = true
  try {
    const view = await createApp(appName)
    await loadServices(true)
    currentApp.value = appName
    applyConfig(view)
    ElMessage.success(`已创建 ${appName}，共 ${(view.items || []).length} 项默认配置`)
  } catch (e) {
    ElMessage.error(e.message || '创建服务失败')
  } finally {
    creating.value = false
  }
}

async function reload() {
  try {
    await loadServices(true)
    await loadConfig(currentApp.value)
    selectedInstance.value = ''
    remoteEffective.value = null
  } catch (e) {
    ElMessage.error(e.message || '加载服务列表失败')
  }
}

onMounted(async () => {
  try {
    await loadServices(false)
  } catch (e) {
    ElMessage.error(e.message || '加载服务列表失败')
    return
  }
  await loadConfig(currentApp.value)
})

defineExpose({ refresh: reload })
</script>

<style scoped>
.config-dirty td {
  background: #f8fbff;
}
</style>
