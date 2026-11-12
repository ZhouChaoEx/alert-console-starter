<template>
  <el-drawer v-model="visible" title="告警详情" size="560px">
    <div v-if="alert">
      <div class="detail-tags">
        <span class="badge blue">{{ alert.appName }}</span>
        <span class="badge" :class="typeClass(alert.alertType)">{{ alert.alertType }}</span>
        <span class="badge" :class="levelClass(alert.alertLevel)">{{ alert.alertLevel }}</span>
      </div>

      <dl class="detail-grid">
        <dt>标题</dt><dd>{{ alert.title }}</dd>
        <dt>告警时间</dt><dd class="mono">{{ alert.alertTime || '-' }}</dd>
        <dt>首次时间</dt><dd class="mono">{{ alert.firstTime || '-' }}</dd>
        <dt>请求 URI</dt><dd class="mono">{{ alert.requestUri || '-' }}</dd>
        <dt>耗时</dt><dd>{{ alert.costMs == null ? '-' : alert.costMs + 'ms' }}</dd>
        <dt>traceId</dt><dd class="mono">{{ alert.traceId || '-' }}</dd>
        <dt>重复次数</dt><dd>{{ alert.repeatCount }}</dd>
        <dt>异常类型</dt><dd class="mono">{{ alert.exceptionType || '-' }}</dd>
        <dt>内容</dt><dd>{{ alert.content || '-' }}</dd>
      </dl>

      <h4>堆栈 / SQL / 扩展信息</h4>
      <pre class="code-block">{{ detailText }}</pre>
    </div>
    <el-skeleton v-else :rows="6" animated />
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlert } from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  alertId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const alert = ref(null)

const detailText = computed(() => {
  if (!alert.value) {
    return ''
  }
  return alert.value.stackTrace || alert.value.sqlText || alert.value.extra || '无'
})

watch(
  () => props.alertId,
  async id => {
    if (id == null) {
      alert.value = null
      return
    }
    try {
      alert.value = await getAlert(id)
    } catch (e) {
      alert.value = null
      ElMessage.error(e.message || '加载告警详情失败')
    }
  },
  { immediate: true }
)

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
</script>

<style scoped>
.detail-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 10px 12px;
  font-size: 13px;
}
.detail-grid dt {
  color: var(--muted);
}
.detail-grid dd {
  margin: 0;
  word-break: break-all;
}
h4 {
  margin: 18px 0 8px;
  font-size: 13px;
  color: #475569;
}
.code-block {
  background: #0f172a;
  color: #dbeafe;
  border-radius: 9px;
  padding: 12px;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
}
</style>
