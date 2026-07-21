<script setup lang="ts">
// 聊天主界面：三栏布局（会话列表 + 聊天窗口 + 会话详情）
// 使用原型风格的 .modal-overlay/.modal 自定义弹窗，替代 el-dialog
import { ref, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import ChatLayout from '@/layouts/ChatLayout.vue'
import ConversationList from '@/components/chat/ConversationList.vue'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNotificationStore } from '@/stores/notification'
import { useThemeStore } from '@/stores/theme'
import { useAiStream } from '@/composables/useAiStream'
import { getAvatarText, isAvatarUrl, resolveUploadUrl, groupFriendsByLetter, getPinyinFirstLetter, formatChatTime, formatNotifTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import * as friendApi from '@/api/friend'
import * as groupApi from '@/api/group'
import * as userApi from '@/api/user'
import { uploadFile } from '@/api/file'
import type { Conversation, Friend, FriendRequest, GroupMember, Message } from '@/types'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const notificationStore = useNotificationStore()
const themeStore = useThemeStore()
const { ask: askAi, streaming: aiStreaming } = useAiStream()

// === 用户在线状态选择 ===
// 0-离线 1-在线 2-忙碌 3-离开
const STATUS_OPTIONS = [
  { value: 1, label: '在线', dotClass: 'online' },
  { value: 2, label: '忙碌', dotClass: 'busy' },
  { value: 3, label: '离开', dotClass: 'away' },
  { value: 0, label: '离线', dotClass: 'offline' }
]
const statusMenuVisible = ref(false)
const currentUserStatus = computed(() => authStore.user?.status ?? 1)
const currentStatusOption = computed(
  () => STATUS_OPTIONS.find((s) => s.value === currentUserStatus.value) || STATUS_OPTIONS[0]
)

function toggleStatusMenu() {
  statusMenuVisible.value = !statusMenuVisible.value
}

async function handleChangeStatus(status: number) {
  statusMenuVisible.value = false
  if (status === currentUserStatus.value) return
  try {
    await userApi.updateStatus(status)
    // 同步更新本地缓存的用户信息
    if (authStore.user) {
      authStore.setUser({ ...authStore.user, status })
    }
    toast.success('状态已更新', `当前状态：${STATUS_OPTIONS.find((s) => s.value === status)?.label}`)
  } catch {
    toast.error('更新失败', '状态更新失败，请稍后重试')
  }
}

// 会话筛选标签
const activeTab = ref<'all' | 'private' | 'group'>('all')
const searchKeyword = ref('')
const messageAreaRef = ref<HTMLElement | null>(null)
// 右栏会话详情面板显隐（由顶栏三点图标切换）
const detailPanelVisible = ref(true)

// === 弹窗状态 ===
const friendModalVisible = ref(false)
const groupModalVisible = ref(false)
const inviteModalVisible = ref(false)
const confirmModalVisible = ref(false)

const friendSearchKeyword = ref('')
const groupName = ref('')
const newGroupAvatar = ref<string>('') // 创建群组时的 base64 头像
const groupAvatarInputRef = ref<HTMLInputElement | null>(null) // 创建群组头像文件选择
const detailAvatarInputRef = ref<HTMLInputElement | null>(null) // 会话详情头像文件选择
const avatarUploading = ref(false) // 头像上传中状态
const editingGroupName = ref(false) // 群名编辑模式
const tempGroupName = ref('') // 群名编辑临时值
const groupNameEditRef = ref<HTMLInputElement | null>(null) // 群名编辑输入框引用
const pickedMemberIds = ref<string[]>([])
const invitePickedIds = ref<string[]>([])
const pickableFriends = ref<Friend[]>([])

// 创建群组弹窗中展示的已有群聊（按角色分类）
const myAllGroups = ref<Conversation[]>([])
const myCreatedGroups = computed(() => myAllGroups.value.filter((g) => g.myRole === 2))
const myManagedGroups = computed(() => myAllGroups.value.filter((g) => g.myRole === 1))
const myJoinedGroups = computed(() => myAllGroups.value.filter((g) => g.myRole !== 2 && g.myRole !== 1))

// 群组管理弹窗 Tab：创建群组 / 我创建的 / 我加入的
type GroupModalTab = 'create' | 'created' | 'joined'
const groupModalTab = ref<GroupModalTab>('create')

// 好友管理 Tab：搜索添加 / 待处理 / 已发送 / 我的好友
const friendActiveTab = ref<'search' | 'pending' | 'sent' | 'friends'>('search')
const pendingRequests = ref<FriendRequest[]>([])
const sentRequests = ref<FriendRequest[]>([])

// 我的好友按拼音首字母分组（微信通讯录风格 A-#）
const groupedFriends = computed(() => groupFriendsByLetter(pickableFriends.value))

// 群成员（由 API 加载）
const groupMembers = ref<GroupMember[]>([])

// 已在当前群中的好友ID集合（邀请弹窗中这些好友不可再选）
const inviteInGroupIds = computed(() => new Set(groupMembers.value.map((m) => m.id)))

// 邀请弹窗中实际新选（不在群中）的好友数量
const inviteNewPickedCount = computed(
  () => invitePickedIds.value.filter((id) => !inviteInGroupIds.value.has(id)).length
)

// === 右键菜单 ===
interface ContextMenuItem {
  label?: string
  action?: string
  icon?: string
  danger?: boolean
  divider?: boolean
  onClick?: () => void
}
const contextMenu = ref<{ visible: boolean; x: number; y: number; items: ContextMenuItem[] }>({
  visible: false,
  x: 0,
  y: 0,
  items: []
})

// === 确认弹窗 ===
const confirmState = ref<{ title: string; message: string; onConfirm: (() => void) | null }>({
  title: '',
  message: '',
  onConfirm: null
})

// 过滤后的会话列表（置顶在前，置顶区内/普通区内均按最后消息时间倒序）
const filteredConversations = computed(() => {
  let list = chatStore.conversations
  if (activeTab.value === 'private') list = list.filter((c) => c.type === 'private')
  if (activeTab.value === 'group') list = list.filter((c) => c.type === 'group')
  if (searchKeyword.value) {
    list = list.filter((c) => c.name.includes(searchKeyword.value))
  }
  // 排序：置顶在前；同一区内按 lastTime 倒序（无时间排末尾）
  return [...list].sort((a, b) => {
    const ap = a.pinned ? 1 : 0
    const bp = b.pinned ? 1 : 0
    if (ap !== bp) return bp - ap
    const at = a.lastTime ? new Date(a.lastTime).getTime() : 0
    const bt = b.lastTime ? new Date(b.lastTime).getTime() : 0
    return bt - at
  })
})

// 当前会话
const currentConversation = computed(() => chatStore.currentConversation)

// 当前消息列表
const currentMessages = computed(() => chatStore.currentMessages)

/**
 * 判断某条消息是否需要显示时间分隔符。
 * 规则：首条消息显示；与上一条消息时间间隔超过 5 分钟则显示。
 */
function shouldShowTimeDivider(index: number): boolean {
  if (index === 0) return true
  const list = currentMessages.value
  const prev = list[index - 1]
  const curr = list[index]
  if (!prev || !curr) return false
  const diff = Math.abs(new Date(curr.time).getTime() - new Date(prev.time).getTime())
  return diff > 5 * 60 * 1000
}

// 格式化时间分隔符文本
function timeDividerText(time: string): string {
  return formatChatTime(time)
}

// 是否群聊
const isGroup = computed(() => currentConversation.value?.type === 'group')

// 私聊对方状态文本（0-离线 1-在线 2-忙碌 3-离开）
const peerStatusLabel = computed(() => {
  const s = currentConversation.value?.peerStatus
  if (s === 1) return '在线'
  if (s === 2) return '忙碌'
  if (s === 3) return '离开'
  return '离线'
})

// 群成员在线人数（按非离线状态统计：在线/忙碌/离开均计入）
const onlineMemberCount = computed(
  () => groupMembers.value.filter((m) => (m.status ?? 0) !== 0).length
)

// 群成员状态点样式：0-离线 1-在线 2-忙碌 3-离开
function memberStatusDotClass(status?: number): string {
  switch (Number(status ?? 0)) {
    case 1:
      return 'online'
    case 2:
      return 'busy'
    case 3:
      return 'away'
    default:
      return 'offline'
  }
}

// 当前用户是否为群主（用于显示移除成员按钮）
const isGroupOwner = computed(() => {
  const conv = currentConversation.value
  if (!conv || conv.type !== 'group' || !conv.ownerId) return false
  return String(authStore.user?.id) === String(conv.ownerId)
})

// 群成员排序：群主置顶，其余按名字拼音首字母排序（与好友排序规则一致）
const sortedGroupMembers = computed(() => {
  const list = [...groupMembers.value]
  list.sort((a, b) => {
    const aOwner = a.role === 'owner' ? 0 : 1
    const bOwner = b.role === 'owner' ? 0 : 1
    if (aOwner !== bOwner) return aOwner - bOwner
    // 同为成员：按拼音首字母排序，# 排最后
    const la = getPinyinFirstLetter(a.name)
    const lb = getPinyinFirstLetter(b.name)
    if (la === '#' && lb !== '#') return 1
    if (lb === '#' && la !== '#') return -1
    if (la !== lb) return la.localeCompare(lb)
    return a.name.localeCompare(b.name, 'zh')
  })
  return list
})

// 选中会话
async function handleSelectConversation(id: string) {
  await chatStore.selectConversation(id)
  scrollToBottom()
  // 群聊加载成员列表
  if (currentConversation.value?.type === 'group') {
    loadGroupMembers(currentConversation.value.id)
  } else {
    groupMembers.value = []
  }
}

// 加载群成员（群聊使用会话ID作为群ID）
async function loadGroupMembers(conversationId: string) {
  try {
    groupMembers.value = await groupApi.getGroupMembers(conversationId)
  } catch (e) {
    console.error('[ChatView.loadGroupMembers] 加载失败:', e)
    groupMembers.value = []
  }
}

// 发送消息
async function handleSend(content: string, mentionAI: boolean) {
  if (!currentConversation.value) {
    toast.warning('请先选择会话', '从左侧选择一个会话后再发送消息')
    return
  }
  const conv = currentConversation.value
  // AI 会话或 @AI：走 AI 流式回复
  if (conv.isAI || mentionAI) {
    await handleAiReply(conv, content, mentionAI)
    return
  }
  // 普通消息
  await chatStore.sendMessage({
    conversationId: conv.id,
    type: 'TEXT',
    content,
    mentionAI
  })
  scrollToBottom()
}

// AI 回复处理
// fullContent: 用户输入的完整内容（@AI 场景下含 @AI 前缀，独立AI会话为纯问题）
// mentionAI: 是否通过 @AI 触发（独立AI会话为 false）
async function handleAiReply(conv: Conversation, fullContent: string, mentionAI: boolean) {
  // 1. 持久化用户提问到会话（保留 @AI 前缀）+ WebSocket 广播给所有成员
  await chatStore.sendMessage({
    conversationId: conv.id,
    type: 'TEXT',
    content: fullContent,
    mentionAI
  })
  scrollToBottom()

  // 2. 提取实际问题内容（去除 @AI 前缀）作为 AI 提问 prompt
  const prompt = fullContent.replace(/@AI\s*/gi, '').trim()

  // 3. 调用 AI 流式回复（后端落库 AI 回复到该会话 + WebSocket 广播）
  await askAi(conv.id, prompt)
  scrollToBottom()
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageAreaRef.value) {
      messageAreaRef.value.scrollTop = messageAreaRef.value.scrollHeight
    }
  })
}

// 上拉加载更多时跳过自动滚动到底部
const skipAutoScroll = ref(false)

// 监听消息变化自动滚动（加载更多历史消息时跳过，保持滚动位置）
watch(
  () => currentMessages.value.length,
  () => {
    if (!skipAutoScroll.value) scrollToBottom()
  }
)

// 滚动事件：接近顶部时上拉加载更多历史消息
async function handleScroll() {
  const area = messageAreaRef.value
  if (!area) return
  if (area.scrollTop > 50) return
  const convId = chatStore.currentConversation?.id
  if (!convId) return
  if (chatStore.loadingMore || !chatStore.hasMoreMap[convId]) return
  // 记录加载前的 scrollHeight，用于恢复滚动位置
  const oldScrollHeight = area.scrollHeight
  skipAutoScroll.value = true
  try {
    const loaded = await chatStore.loadMoreMessages(convId)
    if (loaded > 0) {
      nextTick(() => {
        if (messageAreaRef.value) {
          const newScrollHeight = messageAreaRef.value.scrollHeight
          messageAreaRef.value.scrollTop = newScrollHeight - oldScrollHeight
        }
      })
    }
  } finally {
    skipAutoScroll.value = false
  }
}

// 图片上传
async function handleUploadImage(file: File) {
  if (!currentConversation.value) {
    toast.warning('请先选择会话', '从左侧选择一个会话后再发送图片')
    return
  }
  const conv = currentConversation.value
  toast.info('上传中', '正在上传图片…')
  try {
    const result = await uploadFile(file)
    await chatStore.sendMessage({
      conversationId: conv.id,
      type: 'IMAGE',
      content: result.url,
      extra: JSON.stringify({ fileName: result.fileName, fileSize: result.fileSize })
    })
    scrollToBottom()
  } catch {
    toast.error('上传失败', '图片上传失败，请稍后重试')
  }
}

// 文件上传
async function handleUploadFile(file: File) {
  if (!currentConversation.value) {
    toast.warning('请先选择会话', '从左侧选择一个会话后再发送文件')
    return
  }
  const conv = currentConversation.value
  toast.info('上传中', '正在上传文件…')
  try {
    const result = await uploadFile(file)
    await chatStore.sendMessage({
      conversationId: conv.id,
      type: 'FILE',
      content: result.url,
      extra: JSON.stringify({ fileName: result.fileName, fileSize: result.fileSize })
    })
    scrollToBottom()
  } catch {
    toast.error('上传失败', '文件上传失败，请稍后重试')
  }
}

// === 右键菜单 ===
function showContextMenu(e: MouseEvent, items: ContextMenuItem[]) {
  contextMenu.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY,
    items
  }
}

function hideContextMenu() {
  contextMenu.value.visible = false
}

// 会话项右键
function onConversationContextmenu(payload: { event: MouseEvent; conversation: Conversation }) {
  const conv = payload.conversation
  const items: ContextMenuItem[] = [
    {
      label: conv.pinned ? '取消置顶' : '置顶聊天',
      icon: 'pin',
      onClick: async () => {
        const pinned = await chatStore.togglePin(conv.id)
        toast.success(pinned ? '已置顶' : '已取消置顶', `${conv.name} ${pinned ? '已置顶' : '已取消置顶'}`)
      }
    },
    {
      label: conv.muted ? '取消免打扰' : '消息免打扰',
      icon: 'mute',
      onClick: async () => {
        const muted = await chatStore.toggleMute(conv.id)
        toast.success(muted ? '已设置免打扰' : '已取消免打扰', `${conv.name} ${muted ? '已设置免打扰' : '已取消免打扰'}`)
      }
    },
  ]
  // AI 会话暂不支持删除，仅私聊/群聊显示删除入口
  if (!conv.isAI) {
    items.push(
      { divider: true },
      {
        label: '删除会话',
        icon: 'delete',
        danger: true,
        onClick: () => {
          showConfirm('删除会话', `确定要删除与「${conv.name}」的会话吗？\n删除后将清空聊天记录同时不显示会话。`, async () => {
            await chatStore.deleteConversation(conv.id)
            toast.success('会话已删除', `与「${conv.name}」的会话已移除`)
          })
        }
      }
    )
  }
  showContextMenu(payload.event, items)
}

// 消息右键
function onMessageContextmenu(payload: { event: MouseEvent; message: Message }) {
  const msg = payload.message
  const items: ContextMenuItem[] = [
    {
      label: '删除',
      icon: 'delete',
      danger: true,
      onClick: async () => {
        await chatStore.hideMessage(msg.conversationId, msg.id)
        toast.success('消息已删除')
      }
    }
  ]
  showContextMenu(payload.event, items)
}

// 点击外部关闭右键菜单 / 状态菜单
function onDocumentClick(e: MouseEvent) {
  if (contextMenu.value.visible) hideContextMenu()
  const target = e.target as HTMLElement
  if (statusMenuVisible.value && !target.closest('.status-selector')) {
    statusMenuVisible.value = false
  }
  if (notifPanelVisible.value && !target.closest('.notif-trigger')) {
    notifPanelVisible.value = false
  }
  if (themeMenuVisible.value && !target.closest('.theme-trigger')) {
    themeMenuVisible.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  // 拉取通知未读数
  notificationStore.fetchUnreadCount().catch(() => {})
  // 检查 sessionStorage 是否要求自动打开好友管理面板
  if (sessionStorage.getItem('openFriend') === '1') {
    sessionStorage.removeItem('openFriend')
    nextTick(() => openFriendModal())
  }
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})

// === 确认弹窗 ===
function showConfirm(title: string, message: string, onConfirm: () => void) {
  confirmState.value = { title, message, onConfirm }
  confirmModalVisible.value = true
}

function handleConfirm() {
  if (confirmState.value.onConfirm) {
    confirmState.value.onConfirm()
  }
  confirmModalVisible.value = false
}

// === 好友管理 ===
async function openFriendModal() {
  friendModalVisible.value = true
  friendActiveTab.value = 'search'
  const [friends, pending, sent] = await Promise.all([
    friendApi.getFriends(),
    friendApi.getPendingRequests(),
    friendApi.getSentRequests()
  ])
  pickableFriends.value = friends
  pendingRequests.value = pending
  sentRequests.value = sent
}

async function handleAcceptRequest(req: FriendRequest) {
  await friendApi.acceptFriendRequest(req.id)
  toast.success('已添加好友', `已与 ${req.name} 成为好友`)
  pendingRequests.value = pendingRequests.value.filter((r) => r.id !== req.id)
  pickableFriends.value = await friendApi.getFriends()
  chatStore.fetchConversations()
}

async function handleRejectRequest(req: FriendRequest) {
  await friendApi.rejectFriendRequest(req.id)
  toast.info('已拒绝请求', `已拒绝 ${req.name} 的好友请求`)
  pendingRequests.value = pendingRequests.value.filter((r) => r.id !== req.id)
}

async function handleSendFriendRequest() {
  const keyword = friendSearchKeyword.value.trim()
  if (!keyword) {
    toast.warning('请输入邮箱', '请输入好友邮箱后再发送请求')
    return
  }
  // 邮箱格式校验
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(keyword)) {
    toast.warning('邮箱格式错误', '请输入正确的好友邮箱地址')
    return
  }
  // 校验：不能添加自己为好友（后端搜索会排除自己，需在搜索前拦截）
  if (authStore.user?.email && authStore.user.email.toLowerCase() === keyword.toLowerCase()) {
    toast.warning('无法添加', '不能添加自己为好友')
    return
  }
  const users = await friendApi.searchUser(keyword)
  if (!users.length) {
    toast.warning('未找到用户', '没有找到该邮箱对应的好友')
    return
  }
  const targetUid = users[0].id
  await friendApi.sendFriendRequest(targetUid)
  toast.success('请求已发送', `已向 ${keyword} 发送好友请求`)
  friendSearchKeyword.value = ''
  sentRequests.value = await friendApi.getSentRequests()
}

// 点击好友跳转聊天：若本地无会话则调用后端创建/恢复（删除后重新发起，看不到旧消息）
async function handleFriendClick(friendId: string, friendName: string) {
  const existConv = chatStore.conversations.find(
    (c) => c.type === 'private' && (c.targetId === friendId || c.name === friendName)
  )
  if (existConv) {
    await handleSelectConversation(existConv.id)
    friendModalVisible.value = false
    return
  }
  try {
    const conv = await chatStore.createPrivateConversationWith(friendId)
    if (conv) {
      await handleSelectConversation(conv.id)
      friendModalVisible.value = false
    } else {
      toast.info('暂无会话', `与 ${friendName} 还没有聊天记录，可从聊天列表发起对话`)
    }
  } catch (e) {
    console.error('[ChatView.handleFriendClick] 创建私聊失败:', e)
    toast.error('创建会话失败', '无法创建私聊会话，请稍后重试')
  }
}

// === 删除好友 ===
function handleDeleteFriend() {
  const conv = currentConversation.value
  if (!conv) return
  showConfirm(
    '删除好友',
    `确定要删除好友「${conv.name}」吗？删除后将同时清空与该好友的聊天记录，且对方不会再收到你的消息。`,
    async () => {
      const friendId = conv.targetId || conv.id
      await friendApi.deleteFriend(friendId)
      // 从会话列表移除
      const idx = chatStore.conversations.findIndex((c) => c.id === conv.id)
      if (idx !== -1) chatStore.conversations.splice(idx, 1)
      // 清空消息
      delete chatStore.messageMap[conv.id]
      // 切回空状态
      if (currentConversation.value?.id === conv.id) {
        chatStore.currentConversation = null
      }
      toast.success('已删除好友', `已与 ${conv.name} 解除好友关系`)
    }
  )
}

// === 创建群组 ===
async function openGroupModal() {
  groupModalVisible.value = true
  groupModalTab.value = 'create'
  groupName.value = ''
  newGroupAvatar.value = ''
  pickedMemberIds.value = []
  pickableFriends.value = await friendApi.getFriends()
  // 加载已有群聊（含已从本地列表移除的），按角色分类展示
  try {
    myAllGroups.value = await chatStore.fetchAllGroupConversations()
  } catch (e) {
    console.error('[ChatView.openGroupModal] 加载群聊列表失败:', e)
    myAllGroups.value = []
  }
}

// 重新打开已存在的群聊会话（从群组管理弹窗点击跳转）
// 若会话已从列表删除，则调用 rejoin 恢复
async function handleReopenGroup(conv: Conversation) {
  groupModalVisible.value = false
  // 检查会话是否已在本地列表中
  const exist = chatStore.conversations.find((c) => c.id === conv.id)
  if (!exist) {
    // 会话已删除，调用 rejoin 恢复
    try {
      await chatStore.rejoinGroup(conv.id)
    } catch {
      toast.error('打开失败', '无法恢复该群聊会话')
      return
    }
  }
  await handleSelectConversation(conv.id)
  toast.success('已打开', `已进入「${conv.name}」`)
}

function togglePickMember(id: string) {
  const idx = pickedMemberIds.value.indexOf(id)
  if (idx === -1) pickedMemberIds.value.push(id)
  else pickedMemberIds.value.splice(idx, 1)
}

async function handleCreateGroup() {
  if (!groupName.value.trim()) {
    toast.warning('请输入群名称', '为你的群组起个名字')
    return
  }
  if (pickedMemberIds.value.length === 0) {
    toast.warning('请选择成员', '请至少选择一名好友加入群组')
    return
  }
  await groupApi.createGroup({
    name: groupName.value,
    avatar: newGroupAvatar.value || undefined,
    memberIds: pickedMemberIds.value
  })
  toast.success('创建成功', `群组「${groupName.value}」已创建`)
  groupModalVisible.value = false
  chatStore.fetchConversations()
}

// === 群头像上传 ===

/** 文件转 base64 */
function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

/** 触发创建群组头像文件选择 */
function triggerGroupAvatarUpload() {
  groupAvatarInputRef.value?.click()
}

/** 创建群组头像选择回调 */
async function handleGroupAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    toast.warning('头像过大', '请选择 2MB 以内的图片')
    input.value = ''
    return
  }
  newGroupAvatar.value = await fileToBase64(file)
  input.value = ''
}

/** 触发会话详情面板群头像上传（仅群主/管理员） */
function triggerDetailAvatarUpload() {
  if (!isGroupOwner.value) return
  detailAvatarInputRef.value?.click()
}

/** 会话详情面板群头像选择回调：上传 MinIO → 更新群信息 */
async function handleDetailAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !currentConversation.value) return
  if (file.size > 2 * 1024 * 1024) {
    toast.warning('头像过大', '请选择 2MB 以内的图片')
    input.value = ''
    return
  }
  avatarUploading.value = true
  try {
    const base64 = await fileToBase64(file)
    const url = await groupApi.uploadGroupAvatar(currentConversation.value.id, base64)
    await groupApi.updateGroup(currentConversation.value.id, { avatar: url })
    // 更新本地会话数据
    currentConversation.value.avatar = url
    chatStore.fetchConversations()
    toast.success('头像更新成功')
  } catch (err) {
    console.error('[ChatView.handleDetailAvatarChange] 上传群头像失败:', err)
    toast.error('上传失败', '请稍后重试')
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}

// === 群名编辑 ===

/** 进入群名编辑模式 */
function startEditGroupName() {
  if (!currentConversation.value) return
  tempGroupName.value = currentConversation.value.name
  editingGroupName.value = true
  nextTick(() => {
    groupNameEditRef.value?.focus()
    groupNameEditRef.value?.select()
  })
}

/** 保存群名 */
async function saveGroupName() {
  if (!editingGroupName.value || !currentConversation.value) return
  editingGroupName.value = false
  const newName = tempGroupName.value.trim()
  if (!newName || newName === currentConversation.value.name) return
  try {
    await groupApi.updateGroup(currentConversation.value.id, { name: newName })
    currentConversation.value.name = newName
    chatStore.fetchConversations()
    toast.success('群名称已更新')
  } catch (err) {
    console.error('[ChatView.saveGroupName] 修改群名失败:', err)
    toast.error('修改失败', '请稍后重试')
  }
}

/** 取消群名编辑 */
function cancelEditGroupName() {
  editingGroupName.value = false
  tempGroupName.value = ''
}

// === 邀请成员 ===
async function openInviteModal() {
  if (!currentConversation.value) return
  inviteModalVisible.value = true
  pickableFriends.value = await friendApi.getFriends()
  // 已在群中的好友默认勾选但不可操作
  invitePickedIds.value = pickableFriends.value
    .filter((f) => inviteInGroupIds.value.has(f.id))
    .map((f) => f.id)
}

function toggleInvitePick(id: string) {
  // 已在群中的好友不可切换选中状态
  if (inviteInGroupIds.value.has(id)) return
  const idx = invitePickedIds.value.indexOf(id)
  if (idx === -1) invitePickedIds.value.push(id)
  else invitePickedIds.value.splice(idx, 1)
}

async function handleInviteMembers() {
  if (!currentConversation.value) return
  // 仅邀请不在群中的好友
  const toInvite = invitePickedIds.value.filter((id) => !inviteInGroupIds.value.has(id))
  if (toInvite.length === 0) {
    toast.warning('请选择好友', '请至少选择一名不在群中的好友邀请入群')
    return
  }
  const targetId = currentConversation.value.targetId || currentConversation.value.id
  try {
    await groupApi.inviteMembers(targetId, toInvite)
    toast.success('邀请成功', `已邀请 ${toInvite.length} 位好友加入群聊`)
    inviteModalVisible.value = false
    // 重新加载成员列表
    loadGroupMembers(currentConversation.value.id)
  } catch {
    toast.error('邀请失败', '邀请成员入群失败，请稍后重试')
  }
}

// === 私聊快捷操作 ===
async function handleQuickMute() {
  if (!currentConversation.value) return
  const muted = await chatStore.toggleMute(currentConversation.value.id)
  toast.success(muted ? '已设置免打扰' : '已取消免打扰', `${currentConversation.value.name} ${muted ? '已设置免打扰' : '已取消免打扰'}`)
}

async function handleQuickPin() {
  if (!currentConversation.value) return
  const pinned = await chatStore.togglePin(currentConversation.value.id)
  toast.success(pinned ? '已置顶' : '已取消置顶', `${currentConversation.value.name} ${pinned ? '已置顶' : '已取消置顶'}`)
}

function handleQuickClearHistory() {
  if (!currentConversation.value) return
  const conv = currentConversation.value
  showConfirm('清空聊天记录', `确定要清空与「${conv.name}」的聊天记录吗？\n清空后你将无法看到此前的消息，但对方仍可正常查看。`, async () => {
    await chatStore.clearHistory(conv.id)
    toast.success('已清空', `与「${conv.name}」的聊天记录已清空`)
  })
}

// === 退出群组 ===
function handleLeaveGroup() {
  if (!currentConversation.value) return
  const conv = currentConversation.value
  const isOwner = isGroupOwner.value
  // 群主退出 = 解散群组（成员保留会话但禁言）
  const title = isOwner ? '解散群组' : '退出群组'
  const message = isOwner
    ? `你是群主，退出将直接解散「${conv.name}」。群成员会保留会话但无法继续发送消息，需手动删除会话。确定解散吗？`
    : `确定要退出「${conv.name}」吗？退出后将不再接收该群消息。`
  showConfirm(
    title,
    message,
    async () => {
      await groupApi.leaveGroup(conv.id)
      toast.success(isOwner ? '群组已解散' : '已退出群组', isOwner ? `群组「${conv.name}」已解散` : `你已离开 ${conv.name}`)
      // 群主：会话从自己的列表中清除；普通成员：会话从本地列表清除
      const idx = chatStore.conversations.findIndex((c) => c.id === conv.id)
      if (idx !== -1) chatStore.conversations.splice(idx, 1)
      chatStore.currentConversation = null
      groupMembers.value = []
    }
  )
}

// === 移除群成员（仅群主可操作） ===
function handleRemoveMember(member: GroupMember) {
  if (!currentConversation.value || !isGroupOwner.value) return
  if (member.role === 'owner') {
    toast.warning('无法移除', '不能移除群主')
    return
  }
  showConfirm(
    '移出群聊',
    `确定要将「${member.name}」移出本群吗？移出后该成员将无法再参与群内对话。`,
    async () => {
      try {
        await groupApi.removeMember(currentConversation.value!.id, member.id)
        toast.success('已移出群聊', `${member.name} 已被移出本群`)
        // 重新加载成员列表
        await loadGroupMembers(currentConversation.value!.id)
      } catch {
        toast.error('移除失败', '移除成员失败，请稍后重试')
      }
    }
  )
}

// 跳转个人中心
function goProfile() {
  router.push('/profile')
}

// 跳转首页
function goLanding() {
  router.push('/')
}

// === 消息通知 ===
const notifPanelVisible = ref(false)

async function toggleNotifPanel() {
  notifPanelVisible.value = !notifPanelVisible.value
  if (notifPanelVisible.value && notificationStore.notifications.length === 0) {
    await notificationStore.fetchList()
  }
}

async function handleNotifRead(id: string) {
  await notificationStore.markAsRead(id)
}

async function handleNotifReadAll() {
  await notificationStore.markAllAsRead()
}

async function handleNotifDelete(id: string) {
  await notificationStore.deleteNotification(id)
}

// === 主题切换 ===
const themeMenuVisible = ref(false)

function toggleThemeMenu() {
  themeMenuVisible.value = !themeMenuVisible.value
}

function handleThemeChange(mode: 'light' | 'dark' | 'auto') {
  themeStore.setMode(mode)
  themeMenuVisible.value = false
}

const themeIconName = computed(() => {
  if (themeStore.mode === 'light') return 'Sunny'
  if (themeStore.mode === 'dark') return 'Moon'
  return 'Monitor'
})
</script>

<template>
  <ChatLayout>
    <div class="chat-app">
      <!-- 左栏：会话列表 -->
      <aside class="sidebar">
        <div class="sidebar-header">
          <!-- 当前用户 -->
          <div class="sidebar-user">
            <div class="avatar size-md" :style="!isAvatarUrl(authStore.user?.avatar) ? { background: '#2563EB' } : {}">
              <img v-if="isAvatarUrl(authStore.user?.avatar)" :src="resolveUploadUrl(authStore.user?.avatar)" alt="头像" />
              <template v-else>{{ getAvatarText(authStore.user?.nickname || 'U') }}</template>
              <span class="status-dot" :class="currentStatusOption.dotClass"></span>
            </div>
            <div class="user-meta status-selector">
              <div class="user-name">{{ authStore.user?.nickname || 'ChatVibe 用户' }}</div>
              <button class="user-status" @click.stop="toggleStatusMenu" title="点击切换在线状态">
                <span class="dot" :class="currentStatusOption.dotClass"></span>
                {{ currentStatusOption.label }} · ChatVibe
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="margin-left: 2px"><polyline points="6 9 12 15 18 9"></polyline></svg>
              </button>
              <!-- 状态选择下拉菜单 -->
              <div v-if="statusMenuVisible" class="status-menu" @click.stop>
                <div
                  v-for="opt in STATUS_OPTIONS"
                  :key="opt.value"
                  class="status-menu-item"
                  :class="{ active: opt.value === currentUserStatus }"
                  @click="handleChangeStatus(opt.value)"
                >
                  <span class="dot" :class="opt.dotClass"></span>
                  <span class="status-menu-label">{{ opt.label }}</span>
                  <svg v-if="opt.value === currentUserStatus" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                </div>
              </div>
            </div>
            <a class="icon-btn" title="个人中心" @click="goProfile">
              <el-icon size="18"><Setting /></el-icon>
            </a>
            <!-- 消息通知信封 -->
            <div class="icon-btn-wrap notif-trigger">
              <a class="icon-btn" title="消息通知" @click.stop="toggleNotifPanel">
                <el-icon size="18"><Bell /></el-icon>
                <span v-if="notificationStore.unreadCount > 0" class="notif-badge">{{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}</span>
              </a>
              <div v-if="notifPanelVisible" class="notif-panel" @click.stop>
                <div class="notif-panel-header">
                  <span class="notif-panel-title">消息通知</span>
                  <button v-if="notificationStore.unreadCount > 0" class="notif-action-btn" @click="handleNotifReadAll">全部已读</button>
                </div>
                <div class="notif-panel-body">
                  <div v-if="notificationStore.notifications.length === 0" class="notif-empty">暂无通知</div>
                  <div v-for="item in notificationStore.notifications" :key="item.id" class="notif-item" :class="{ unread: !item.isRead }">
                    <div class="notif-item-main" @click="!item.isRead && handleNotifRead(item.id)">
                      <div class="notif-item-title">{{ item.title }}</div>
                      <div class="notif-item-content">{{ item.content }}</div>
                      <div class="notif-item-time">{{ formatNotifTime(item.createdAt) }}</div>
                    </div>
                    <button class="notif-item-delete" @click.stop="handleNotifDelete(item.id)" title="删除">
                      <el-icon size="14"><Close /></el-icon>
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <!-- 主题切换 -->
            <div class="icon-btn-wrap theme-trigger">
              <a class="icon-btn" title="切换主题" @click.stop="toggleThemeMenu">
                <el-icon size="18"><component :is="themeIconName" /></el-icon>
              </a>
              <div v-if="themeMenuVisible" class="theme-menu" @click.stop>
                <div class="theme-menu-item" :class="{ active: themeStore.mode === 'light' }" @click="handleThemeChange('light')">
                  <el-icon size="16"><Sunny /></el-icon>
                  <span>白天</span>
                </div>
                <div class="theme-menu-item" :class="{ active: themeStore.mode === 'dark' }" @click="handleThemeChange('dark')">
                  <el-icon size="16"><Moon /></el-icon>
                  <span>黑夜</span>
                </div>
                <div class="theme-menu-item" :class="{ active: themeStore.mode === 'auto' }" @click="handleThemeChange('auto')">
                  <el-icon size="16"><Monitor /></el-icon>
                  <span>自动</span>
                </div>
              </div>
            </div>
          </div>
          <!-- 搜索 -->
          <div class="search-box">
            <el-icon size="14"><Search /></el-icon>
            <input v-model="searchKeyword" type="text" placeholder="搜索好友 / 群组" />
          </div>
        </div>

        <!-- Tab 切换 -->
        <div class="conversation-tabs">
          <button class="conv-tab" :class="{ active: activeTab === 'all' }" @click="activeTab = 'all'">
            全部 <span class="count">{{ chatStore.conversations.length }}</span>
          </button>
          <button class="conv-tab" :class="{ active: activeTab === 'private' }" @click="activeTab = 'private'">
            私聊 <span class="count">{{ chatStore.privateConversations.length }}</span>
          </button>
          <button class="conv-tab" :class="{ active: activeTab === 'group' }" @click="activeTab = 'group'">
            群聊 <span class="count">{{ chatStore.groupConversations.length }}</span>
          </button>
        </div>

        <!-- 会话列表 -->
        <ConversationList
          :conversations="filteredConversations"
          :active-id="currentConversation?.id"
          @select="handleSelectConversation"
          @contextmenu="onConversationContextmenu"
        />

        <!-- 底部按钮 -->
        <div style="padding: 12px 14px; border-top: 1px solid var(--c-border-soft); display: flex; gap: 8px">
          <el-button class="btn-block btn-sm" @click="openFriendModal">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M22 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            好友管理
          </el-button>
          <el-button type="primary" class="btn-block btn-sm" @click="openGroupModal">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            群组管理
          </el-button>
        </div>
      </aside>

      <!-- 中栏：聊天窗口 -->
      <main class="chat-main">
        <template v-if="currentConversation">
          <!-- 顶栏 -->
          <div class="chat-topbar">
            <div class="topbar-info">
              <div class="topbar-name">
                {{ currentConversation.name }}
                <span v-if="currentConversation.isAI" class="chip chip-ai">AI</span>
                <span v-else-if="isGroup" class="chip chip-blue">群聊</span>
              </div>
              <div class="topbar-sub">
                <template v-if="isGroup">
                  {{ currentConversation.members || 0 }} 位成员 · {{ onlineMemberCount }} 人在线
                </template>
                <template v-else>{{ peerStatusLabel }}</template>
              </div>
            </div>
            <div class="topbar-actions">
              <button class="topbar-action" title="搜索消息" @click="toast.info('搜索消息', '暂未开放，敬请期待')">
                <el-icon size="18"><Search /></el-icon>
              </button>
              <button class="topbar-action" title="语音通话" @click="toast.info('语音通话', '暂未开放，敬请期待')">
                <el-icon size="18"><Phone /></el-icon>
              </button>
              <button class="topbar-action" title="视频通话" @click="toast.info('视频通话', '暂未开放，敬请期待')">
                <el-icon size="18"><VideoCamera /></el-icon>
              </button>
              <button class="topbar-action" title="会话详情" @click="detailPanelVisible = !detailPanelVisible">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="1"></circle>
                  <circle cx="19" cy="12" r="1"></circle>
                  <circle cx="5" cy="12" r="1"></circle>
                </svg>
              </button>
            </div>
          </div>

          <!-- 消息区 -->
          <div ref="messageAreaRef" class="message-area scroll-area" @scroll="handleScroll">
            <!-- 上拉加载指示器 -->
            <div v-if="chatStore.loadingMore" class="msg-load-more">加载中...</div>
            <template v-for="(msg, idx) in currentMessages" :key="msg.id">
              <!-- 时间分隔符：首条消息显示，或与上一条间隔超过 5 分钟时显示 -->
              <div v-if="shouldShowTimeDivider(idx)" class="msg-time-divider">
                {{ timeDividerText(msg.time) }}
              </div>
              <MessageBubble
                :message="msg"
                :show-avatar="isGroup || msg.sender === 'ai'"
                @contextmenu="onMessageContextmenu"
              />
            </template>
            <!-- <div v-if="currentMessages.length === 0" class="text-center text-muted" style="padding: 60px 12px">
              开始对话，发送第一条消息吧
            </div> -->
          </div>

          <!-- 输入区 -->
          <MessageInput
            :disabled="aiStreaming"
            :dissolved="isGroup && !!currentConversation?.dissolved"
            @send="handleSend"
            @upload-image="handleUploadImage"
            @upload-file="handleUploadFile"
          />
        </template>

        <!-- 未选中会话占位 -->
        <div v-else class="flex-col items-center justify-center" style="flex: 1; gap: 16px">
          <div class="logo-mark" style="width: 64px; height: 64px; border-radius: 18px; cursor: pointer" @click="goLanding">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
            </svg>
          </div>
          <div class="text-center">
            <div style="font-size: 18px; font-weight: 700; font-family: 'Sora', sans-serif">欢迎使用 ChatVibe</div>
            <div class="text-muted text-sm" style="margin-top: 6px">从左侧选择一个会话开始聊天</div>
          </div>
        </div>
      </main>

      <!-- 右栏：会话详情 -->
      <aside v-if="currentConversation && detailPanelVisible" class="detail-panel scroll-area">
        <div class="detail-header">
          <h3>会话详情</h3>
          <button class="modal-close" title="关闭" @click="detailPanelVisible = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="detail-section">
          <div class="detail-group-info">
            <div
              class="avatar size-xl"
              :class="{ 'ai-avatar': currentConversation.isAI, 'avatar-clickable': isGroup && isGroupOwner }"
              :style="!currentConversation.isAI && !isAvatarUrl(currentConversation.avatar) && currentConversation.color ? { background: currentConversation.color } : {}"
              @click="isGroup && isGroupOwner && triggerDetailAvatarUpload()"
            >
              <img v-if="isAvatarUrl(currentConversation.avatar)" :src="resolveUploadUrl(currentConversation.avatar)" alt="头像" />
              <template v-else>{{ currentConversation.avatar || getAvatarText(currentConversation.name) }}</template>
            </div>
            <!-- 群名：群主可编辑 -->
            <div v-if="isGroup && isGroupOwner && editingGroupName" class="group-name-edit">
              <input
                ref="groupNameEditRef"
                v-model="tempGroupName"
                class="input group-name-input"
                maxlength="30"
                @keyup.enter="saveGroupName"
                @keyup.escape="cancelEditGroupName"
                @blur="saveGroupName"
              />
            </div>
            <div v-else class="group-name-row">
              <div class="group-name">{{ currentConversation.name }}</div>
              <button
                v-if="isGroup && isGroupOwner"
                class="group-name-edit-btn"
                title="修改群名称"
                @click="startEditGroupName"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
              </button>
            </div>
            <div v-if="avatarUploading" class="text-muted text-xs" style="margin-top: 2px">上传中...</div>
            <input
              ref="detailAvatarInputRef"
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              style="display: none"
              @change="handleDetailAvatarChange"
            />
          </div>
        </div>

        <!-- 群成员列表 -->
        <div v-if="isGroup" class="detail-section">
          <div class="detail-section-title detail-section-row">
            <span>群成员 ({{ groupMembers.length || currentConversation.members || 0 }})</span>
            <button class="link-btn" @click="openInviteModal">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              邀请成员
            </button>
          </div>
          <div class="member-list">
            <div
              v-for="member in sortedGroupMembers"
              :key="member.id"
              class="member-item"
            >
              <div class="avatar size-sm" :class="{ 'ai-avatar': member.isAI }" :style="!member.isAI && !isAvatarUrl(member.avatar) && member.color ? { background: member.color } : {}">
                <img v-if="isAvatarUrl(member.avatar)" :src="resolveUploadUrl(member.avatar)" alt="头像" />
                <template v-else>{{ member.avatar || getAvatarText(member.name) }}</template>
                <span class="status-dot" :class="memberStatusDotClass(member.status)"></span>
              </div>
              <div class="member-name">{{ member.name }}</div>
              <span v-if="member.role === 'owner'" class="member-role">群主</span>
              <span v-else class="member-role member-role-normal">成员</span>
              <!-- 群主悬停时可移除其他成员 -->
              <button
                v-if="isGroupOwner && member.role !== 'owner'"
                class="kick-btn"
                title="移出群聊"
                @click.stop="handleRemoveMember(member)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
              </button>
            </div>
            <div v-if="groupMembers.length === 0" class="text-muted text-xs text-center" style="padding: 12px">
              成员列表加载中
            </div>
          </div>
          <button class="btn btn-danger btn-block btn-sm" style="margin-top: 8px" @click="handleLeaveGroup">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
            退出群组
          </button>
        </div>

        <!-- AI 会话说明 -->
        <div v-else-if="currentConversation.isAI" class="detail-section">
          <div class="detail-section-title">AI 能力</div>
          <div class="text-sm text-soft" style="line-height: 1.7">
            · 工作事务处理<br />
            · 文案生成与润色<br />
            · 内容总结与摘要<br />
            · 问题解答与建议
          </div>
        </div>

        <!-- 单聊快捷操作 -->
        <div v-else class="detail-section">
          <div class="detail-section-title">快捷操作</div>
          <div class="member-list">
            <div class="member-item quick-action" @click="handleQuickMute">
              <div class="input-tool-btn">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
              </div>
              <div class="member-name">{{ currentConversation?.muted ? '取消免打扰' : '消息免打扰' }}</div>
            </div>
            <div class="member-item quick-action" @click="handleQuickPin">
              <div class="input-tool-btn">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="12" y1="17" x2="12" y2="22"></line>
                  <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"></path>
                </svg>
              </div>
              <div class="member-name">{{ currentConversation?.pinned ? '取消置顶' : '置顶聊天' }}</div>
            </div>
            <div class="member-item quick-action" @click="handleQuickClearHistory">
              <div class="input-tool-btn">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"></polyline>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                </svg>
              </div>
              <div class="member-name">清空聊天记录</div>
            </div>
          </div>
          <!-- 删除好友（非 AI 私聊） -->
          <button class="btn btn-danger btn-block btn-sm" style="margin-top: 12px" @click="handleDeleteFriend">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="8.5" cy="7" r="4"></circle>
              <line x1="18" y1="8" x2="23" y2="13"></line>
              <line x1="23" y1="8" x2="18" y2="13"></line>
            </svg>
            删除好友
          </button>
        </div>
      </aside>
    </div>
  </ChatLayout>

  <!-- ====== 自定义弹窗：好友管理 ====== -->
  <div v-if="friendModalVisible" class="modal-overlay" @click.self="friendModalVisible = false">
    <div class="modal">
      <div class="modal-header">
        <div class="modal-title">好友管理</div>
        <button class="modal-close" @click="friendModalVisible = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <!-- Tab 切换：搜索添加 / 待处理 / 已发送 / 我的好友 -->
        <div class="modal-tabs">
          <div class="modal-tab" :class="{ active: friendActiveTab === 'search' }" @click="friendActiveTab = 'search'">
            搜索添加
          </div>
          <div class="modal-tab" :class="{ active: friendActiveTab === 'pending' }" @click="friendActiveTab = 'pending'">
            待处理 <span v-if="pendingRequests.length" class="tab-badge">{{ pendingRequests.length }}</span>
          </div>
          <div class="modal-tab" :class="{ active: friendActiveTab === 'sent' }" @click="friendActiveTab = 'sent'">
            已发送
          </div>
          <div class="modal-tab" :class="{ active: friendActiveTab === 'friends' }" @click="friendActiveTab = 'friends'">
            我的好友
          </div>
        </div>

        <!-- Tab: 搜索添加（仅邮箱） -->
        <div v-show="friendActiveTab === 'search'">
          <div class="form-group">
            <label class="form-label">邮箱</label>
            <div class="input-wrap">
              <span class="input-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
              </span>
              <input
                v-model="friendSearchKeyword"
                type="email"
                class="input input-with-icon"
                placeholder="输入好友邮箱搜索并发送请求"
                @keyup.enter="handleSendFriendRequest"
              />
            </div>
          </div>
          <button class="btn btn-primary btn-block" @click="handleSendFriendRequest">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
            发送好友请求
          </button>
        </div>

        <!-- Tab: 待处理 -->
        <div v-show="friendActiveTab === 'pending'">
          <div v-for="r in pendingRequests" :key="r.id" class="request-item">
            <div class="avatar size-sm" :style="!isAvatarUrl(r.avatar) && r.color ? { background: r.color } : {}">
              <img v-if="isAvatarUrl(r.avatar)" :src="resolveUploadUrl(r.avatar)" alt="头像" />
              <template v-else>{{ r.avatar || getAvatarText(r.name) }}</template>
            </div>
            <div class="request-meta">
              <div class="request-name">{{ r.name }}</div>
              <div class="request-info">{{ r.info }}</div>
            </div>
            <div class="request-actions">
              <button class="btn btn-primary btn-sm" @click="handleAcceptRequest(r)">
                接受
              </button>
              <button class="btn btn-ghost btn-sm" @click="handleRejectRequest(r)">
                拒绝
              </button>
            </div>
          </div>
          <div v-if="pendingRequests.length === 0" class="text-muted text-xs text-center" style="padding: 32px">
            暂无待处理的好友请求
          </div>
        </div>

        <!-- Tab: 已发送 -->
        <div v-show="friendActiveTab === 'sent'">
          <div v-for="r in sentRequests" :key="r.id" class="request-item">
            <div class="avatar size-sm" :style="!isAvatarUrl(r.avatar) && r.color ? { background: r.color } : {}">
              <img v-if="isAvatarUrl(r.avatar)" :src="resolveUploadUrl(r.avatar)" alt="头像" />
              <template v-else>{{ r.avatar || getAvatarText(r.name) }}</template>
            </div>
            <div class="request-meta">
              <div class="request-name">{{ r.name }}</div>
              <div class="request-info">
                {{ r.info }}
                <span v-if="r.status === 'pending'" class="chip chip-blue" style="margin-left: 4px">等待验证</span>
                <span v-else-if="r.status === 'accepted'" class="chip chip-green" style="margin-left: 4px">已接受</span>
                <span v-else-if="r.status === 'rejected'" class="chip" style="margin-left: 4px">已拒绝</span>
              </div>
            </div>
          </div>
          <div v-if="sentRequests.length === 0" class="text-muted text-xs text-center" style="padding: 32px">
            暂无已发送的好友请求
          </div>
        </div>

        <!-- Tab: 我的好友（A-# 字母分组排序，微信通讯录风格） -->
        <div v-show="friendActiveTab === 'friends'">
          <div class="friend-list-head">
            <span>共 <strong>{{ pickableFriends.length }}</strong> 位好友</span>
            <span class="form-hint-inline">点击好友进入聊天</span>
          </div>
          <div class="member-list friend-grouped" style="max-height: 380px; overflow-y: auto">
            <template v-for="group in groupedFriends" :key="group.letter">
              <div class="friend-index">{{ group.letter }}</div>
              <div
                v-for="f in group.friends"
                :key="f.id"
                class="friend-item"
                @click="handleFriendClick(f.id, f.name)"
              >
                <div class="avatar size-md" :style="!isAvatarUrl(f.avatar) && f.color ? { background: f.color } : {}">
                  <img v-if="isAvatarUrl(f.avatar)" :src="resolveUploadUrl(f.avatar)" alt="头像" />
                  <template v-else>{{ f.avatar || getAvatarText(f.name) }}</template>
                  <span v-if="f.online !== undefined" class="status-dot" :class="f.online ? 'online' : 'offline'"></span>
                </div>
                <div class="friend-meta">
                  <div class="friend-name">{{ f.name }}</div>
                  <div v-if="f.email" class="friend-email">{{ f.email }}</div>
                  <div v-else-if="f.signature" class="friend-email">{{ f.signature }}</div>
                </div>
                <span class="friend-go">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
                </span>
              </div>
            </template>
            <div v-if="pickableFriends.length === 0" class="text-muted text-xs text-center" style="padding: 32px">
              暂无好友
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ====== 自定义弹窗：群组管理（Tab：创建群组 / 我创建的 / 我加入的） ====== -->
  <div v-if="groupModalVisible" class="modal-overlay" @click.self="groupModalVisible = false">
    <div class="modal">
      <div class="modal-header">
        <div class="modal-title">群组管理</div>
        <button class="modal-close" @click="groupModalVisible = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <!-- Tab 切换 -->
      <div class="group-modal-tabs">
        <button
          class="group-modal-tab"
          :class="{ active: groupModalTab === 'create' }"
          @click="groupModalTab = 'create'"
        >
          创建群组
        </button>
        <button
          class="group-modal-tab"
          :class="{ active: groupModalTab === 'created' }"
          @click="groupModalTab = 'created'"
        >
          我创建的 <span class="tab-count">{{ myCreatedGroups.length }}</span>
        </button>
        <button
          class="group-modal-tab"
          :class="{ active: groupModalTab === 'joined' }"
          @click="groupModalTab = 'joined'"
        >
          我加入的 <span class="tab-count">{{ myJoinedGroups.length + myManagedGroups.length }}</span>
        </button>
      </div>

      <div class="modal-body">
        <!-- Tab 1: 创建群组 -->
        <div v-if="groupModalTab === 'create'">
          <div class="form-group">
            <label class="form-label">群头像</label>
            <div class="group-avatar-upload" @click="triggerGroupAvatarUpload">
              <div
                class="avatar size-xl"
                :style="!newGroupAvatar ? { background: '#2563EB' } : {}"
              >
                <img v-if="newGroupAvatar" :src="newGroupAvatar" alt="群头像" />
                <template v-else>{{ groupName.trim() ? groupName.trim().charAt(0).toUpperCase() : '+' }}</template>
              </div>
              <div class="avatar-upload-hint">点击上传</div>
            </div>
            <input
              ref="groupAvatarInputRef"
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              style="display: none"
              @change="handleGroupAvatarChange"
            />
          </div>
          <div class="form-group">
            <label class="form-label">群名称</label>
            <input v-model="groupName" class="input" placeholder="为你的群组起个名字" />
          </div>
          <div class="form-group">
            <label class="form-label">选择成员（已选 {{ pickedMemberIds.length }} 人）</label>
            <div class="member-picker">
              <template v-for="group in groupedFriends" :key="group.letter">
                <div class="friend-index">{{ group.letter }}</div>
                <div
                  v-for="f in group.friends"
                  :key="f.id"
                  class="pick-item"
                  :class="{ selected: pickedMemberIds.includes(f.id) }"
                  @click="togglePickMember(f.id)"
                >
                  <div class="pick-check">
                    <svg v-if="pickedMemberIds.includes(f.id)" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                  </div>
                  <div class="avatar size-sm" :style="!isAvatarUrl(f.avatar) && f.color ? { background: f.color } : {}">
                    <img v-if="isAvatarUrl(f.avatar)" :src="resolveUploadUrl(f.avatar)" alt="头像" />
                    <template v-else>{{ f.avatar || getAvatarText(f.name) }}</template>
                  </div>
                  <div class="member-name">{{ f.name }}</div>
                </div>
              </template>
              <div v-if="pickableFriends.length === 0" class="text-muted text-xs text-center" style="padding: 24px">
                暂无可选好友
              </div>
            </div>
          </div>
        </div>

        <!-- Tab 2: 我创建的群组 -->
        <div v-else-if="groupModalTab === 'created'">
          <div v-if="myCreatedGroups.length > 0" class="my-groups-list">
            <div
              v-for="g in myCreatedGroups"
              :key="g.id"
              class="my-group-item"
              @click="handleReopenGroup(g)"
            >
              <div class="avatar size-sm" :style="!isAvatarUrl(g.avatar) && g.color ? { background: g.color } : {}">
                <img v-if="isAvatarUrl(g.avatar)" :src="resolveUploadUrl(g.avatar)" alt="头像" />
                <template v-else>{{ getAvatarText(g.name) }}</template>
              </div>
              <div class="my-group-name">{{ g.name }}</div>
              <div class="my-group-members text-muted text-xs">{{ g.members || 0 }} 人</div>
            </div>
          </div>
          <div v-else class="text-muted text-xs text-center" style="padding: 32px">
            还没有创建过群组
          </div>
        </div>

        <!-- Tab 3: 我加入的群组（含我管理的） -->
        <div v-else>
          <div v-if="myManagedGroups.length + myJoinedGroups.length > 0" class="my-groups-list">
            <div v-if="myManagedGroups.length > 0" class="my-groups-block">
              <div class="my-groups-title">我管理的（{{ myManagedGroups.length }}）</div>
              <div
                v-for="g in myManagedGroups"
                :key="g.id"
                class="my-group-item"
                @click="handleReopenGroup(g)"
              >
                <div class="avatar size-sm" :style="!isAvatarUrl(g.avatar) && g.color ? { background: g.color } : {}">
                  <img v-if="isAvatarUrl(g.avatar)" :src="resolveUploadUrl(g.avatar)" alt="头像" />
                  <template v-else>{{ getAvatarText(g.name) }}</template>
                </div>
                <div class="my-group-name">{{ g.name }}</div>
                <div class="my-group-members text-muted text-xs">{{ g.members || 0 }} 人</div>
              </div>
            </div>
            <div v-if="myJoinedGroups.length > 0" class="my-groups-block">
              <div class="my-groups-title">我加入的（{{ myJoinedGroups.length }}）</div>
              <div
                v-for="g in myJoinedGroups"
                :key="g.id"
                class="my-group-item"
                @click="handleReopenGroup(g)"
              >
                <div class="avatar size-sm" :style="!isAvatarUrl(g.avatar) && g.color ? { background: g.color } : {}">
                  <img v-if="isAvatarUrl(g.avatar)" :src="resolveUploadUrl(g.avatar)" alt="头像" />
                  <template v-else>{{ getAvatarText(g.name) }}</template>
                </div>
                <div class="my-group-name">{{ g.name }}</div>
                <div class="my-group-members text-muted text-xs">{{ g.members || 0 }} 人</div>
              </div>
            </div>
          </div>
          <div v-else class="text-muted text-xs text-center" style="padding: 32px">
            还没有加入任何群组
          </div>
        </div>
      </div>

      <div v-if="groupModalTab === 'create'" class="modal-footer">
        <button class="btn btn-primary" @click="handleCreateGroup">
          创建群组
        </button>
      </div>
    </div>
  </div>

  <!-- ====== 自定义弹窗：邀请成员入群 ====== -->
  <div v-if="inviteModalVisible" class="modal-overlay" @click.self="inviteModalVisible = false">
    <div class="modal">
      <div class="modal-header">
        <div class="modal-title">邀请成员入群</div>
        <button class="modal-close" @click="inviteModalVisible = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label">选择好友邀请入群（已选 {{ inviteNewPickedCount }} 人）</label>
          <div class="member-picker">
            <template v-for="group in groupedFriends" :key="group.letter">
              <div class="friend-index">{{ group.letter }}</div>
              <div
                v-for="f in group.friends"
                :key="f.id"
                class="pick-item"
                :class="{ selected: invitePickedIds.includes(f.id), disabled: inviteInGroupIds.has(f.id) }"
                @click="toggleInvitePick(f.id)"
              >
                <div class="pick-check">
                  <svg v-if="invitePickedIds.includes(f.id)" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                </div>
                <div class="avatar size-sm" :style="!isAvatarUrl(f.avatar) && f.color ? { background: f.color } : {}">
                  <img v-if="isAvatarUrl(f.avatar)" :src="resolveUploadUrl(f.avatar)" alt="头像" />
                  <template v-else>{{ f.avatar || getAvatarText(f.name) }}</template>
                </div>
                <div class="member-name">{{ f.name }}</div>
                <span v-if="inviteInGroupIds.has(f.id)" class="pick-tag">已在群中</span>
              </div>
            </template>
            <div v-if="pickableFriends.length === 0" class="text-muted text-xs text-center" style="padding: 24px">
              暂无可邀请的好友
            </div>
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-ghost" @click="inviteModalVisible = false">
          取消
        </button>
        <button class="btn btn-primary" @click="handleInviteMembers">
          确认邀请
        </button>
      </div>
    </div>
  </div>

  <!-- ====== 自定义弹窗：确认对话框 ====== -->
  <div v-if="confirmModalVisible" class="modal-overlay" @click.self="confirmModalVisible = false">
    <div class="modal" style="max-width: 400px">
      <div class="modal-header">
        <div class="modal-title">{{ confirmState.title }}</div>
        <button class="modal-close" @click="confirmModalVisible = false">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <div class="confirm-msg">{{ confirmState.message }}</div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-ghost" @click="confirmModalVisible = false">
          取消
        </button>
        <button class="btn btn-danger" @click="handleConfirm">
          确认
        </button>
      </div>
    </div>
  </div>

  <!-- ====== 右键菜单 ====== -->
  <div
    v-if="contextMenu.visible"
    class="context-menu"
    :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
    @click.stop
  >
    <template v-for="(item, idx) in contextMenu.items" :key="idx">
      <div v-if="item.divider" class="ctx-divider"></div>
      <div v-else class="ctx-item" :class="{ danger: item.danger }" @click="hideContextMenu(); item.onClick?.()">
        {{ item.label }}
      </div>
    </template>
  </div>
</template>

<style scoped>
.flex-col { display: flex; flex-direction: column; }
.items-center { align-items: center; }
.justify-center { justify-content: center; }
.text-center { text-align: center; }
.text-muted { color: var(--c-text-muted); }
.text-soft { color: var(--c-text-soft); }
.text-sm { font-size: 13px; }
.text-xs { font-size: 12px; }

/* 群头像上传区域 */
.group-avatar-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.group-avatar-upload .avatar {
  border: 2px dashed var(--c-border);
  transition: border-color 0.2s;
}
.group-avatar-upload:hover .avatar {
  border-color: var(--c-primary);
}
.avatar-upload-hint {
  font-size: 12px;
  color: var(--c-text-muted);
}
.avatar-clickable {
  cursor: pointer;
  transition: opacity 0.2s;
}
.avatar-clickable:hover {
  opacity: 0.8;
}
.group-name-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.group-name-edit {
  display: flex;
  justify-content: center;
}
.group-name-edit-btn {
  background: none;
  border: none;
  color: var(--c-text-muted);
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  border-radius: 4px;
  transition: color 0.2s, background 0.2s;
}
.group-name-edit-btn:hover {
  color: var(--c-primary);
  background: var(--c-bg-hover, rgba(0, 0, 0, 0.05));
}
.group-name-input {
  width: 160px;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  padding: 4px 8px;
}

/* 消息时间分隔符：居中显示在消息上方 */
.msg-time-divider {
  text-align: center;
  font-size: 11px;
  color: var(--c-text-muted);
  padding: 8px 0;
  margin: 0 auto;
  position: relative;
}
.msg-time-divider::before,
.msg-time-divider::after {
  content: '';
  display: inline-block;
  width: 24px;
  height: 1px;
  background: var(--c-border-soft);
  vertical-align: middle;
  margin: 0 8px;
}
/* 上拉加载更多历史消息指示器 */
.msg-load-more {
  text-align: center;
  font-size: 12px;
  color: var(--c-text-muted);
  padding: 8px 0 4px;
}
.conv-tab {
  flex: 1;
  padding: 12px 0;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-muted);
  border-bottom: 2px solid transparent;
  transition: all 0.2s ease;
  background: transparent;
  cursor: pointer;
}
.conv-tab.active { color: var(--c-primary); border-bottom-color: var(--c-primary); }
.conv-tab .count {
  display: inline-block;
  margin-left: 4px;
  padding: 1px 6px;
  font-size: 10px;
  background: var(--c-bg-soft);
  color: var(--c-text-muted);
  border-radius: 999px;
}
.conv-tab.active .count { background: rgba(37, 99, 235, 0.1); color: var(--c-primary); }
.conversation-tabs {
  display: flex;
  padding: 0 18px;
  gap: 4px;
  border-bottom: 1px solid var(--c-border-soft);
}
/* 弹窗内 Tab badge */
.modal-tab .tab-badge {
  display: inline-block;
  margin-left: 4px;
  padding: 1px 6px;
  font-size: 10px;
  background: #EF4444;
  color: #fff;
  border-radius: 999px;
}

/* 群组管理弹窗 Tab */
.group-modal-tabs {
  display: flex;
  padding: 0 20px;
  gap: 4px;
  border-bottom: 1px solid var(--c-border-soft);
}
.group-modal-tab {
  flex: 1;
  padding: 12px 4px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-muted);
  border-bottom: 2px solid transparent;
  transition: all 0.2s ease;
  background: transparent;
  cursor: pointer;
}
.group-modal-tab.active {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary);
}
.group-modal-tab .tab-count {
  display: inline-block;
  margin-left: 4px;
  padding: 1px 6px;
  font-size: 10px;
  background: var(--c-bg-soft);
  color: var(--c-text-muted);
  border-radius: 999px;
}
.group-modal-tab.active .tab-count {
  background: rgba(37, 99, 235, 0.1);
  color: var(--c-primary);
}

/* Tab 中"我创建的/我加入的"群组列表容器 */
.my-groups-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 360px;
  overflow-y: auto;
}
.my-groups-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.my-groups-title {
  font-size: 12px;
  color: var(--c-text-muted);
  font-weight: 600;
  padding: 4px 0;
}
</style>
