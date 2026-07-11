/* ============================================================
 * ChatVibe · Mock 数据（前后端联调前使用）
 * 数据结构与前端 types 对齐，可直接注入 store
 * ============================================================ */
import type {
  Conversation,
  Friend,
  FriendRequest,
  GroupMember,
  Message,
  User
} from '@/types'

/** 是否启用 Mock 模式（后端就绪后置为 false） */
export const USE_MOCK = false

/** 模拟演示验证码 */
export const DEMO_CODE = '123456'

/** 当前登录用户 */
export const MOCK_USER: User = {
  id: 'u_me',
  nickname: '林深',
  email: 'linshen@chatvibe.com',
  avatar: '',
  signature: '做有温度的产品，写有性格的代码',
  online: true,
  emailVerified: true
}

/** 会话列表 */
export const MOCK_CONVERSATIONS: Conversation[] = [
  {
    id: 'c_1',
    type: 'private',
    name: '苏晚',
    avatar: '苏',
    color: '#F472B6',
    lastMessage: '好的，明天见！',
    lastTime: '14:32',
    unread: 3,
    online: true,
    targetId: 'u_suwAn'
  },
  {
    id: 'c_2',
    type: 'group',
    name: '产品设计组',
    avatar: 'PD',
    color: '#2563EB',
    lastMessage: '@AI 帮我总结下今天的会议要点',
    lastTime: '14:18',
    unread: 12,
    online: true,
    members: 8,
    targetId: 'g_2'
  },
  {
    id: 'c_3',
    type: 'private',
    name: '陈墨',
    avatar: '陈',
    color: '#10B981',
    lastMessage: '[图片]',
    lastTime: '13:45',
    unread: 0,
    online: true,
    targetId: 'u_chenmo'
  },
  {
    id: 'c_4',
    type: 'group',
    name: '前端开发小队',
    avatar: 'FE',
    color: '#7C3AED',
    lastMessage: '老王: 这个 bug 我已经修好了',
    lastTime: '12:20',
    unread: 0,
    online: false,
    members: 5,
    targetId: 'g_4'
  },
  {
    id: 'c_5',
    type: 'private',
    name: 'AI 助手',
    avatar: 'AI',
    color: '#7C3AED',
    isAI: true,
    lastMessage: '已为您生成方案，请查收 ✨',
    lastTime: '昨天',
    unread: 0,
    online: true,
    targetId: 'ai_1'
  },
  {
    id: 'c_6',
    type: 'private',
    name: '周野',
    avatar: '周',
    color: '#F59E0B',
    lastMessage: '收到，我安排一下',
    lastTime: '昨天',
    unread: 0,
    online: false,
    targetId: 'u_zhouye'
  },
  {
    id: 'c_7',
    type: 'group',
    name: '读书分享会',
    avatar: 'RD',
    color: '#0EA5E9',
    lastMessage: '本周分享《思考，快与慢》',
    lastTime: '周一',
    unread: 0,
    online: false,
    members: 12,
    targetId: 'g_7'
  }
]

/** 消息记录（按会话 ID 分组） */
export const MOCK_MESSAGES: Record<string, Message[]> = {
  c_1: [
    { id: 'm1', conversationId: 'c_1', sender: 'other', name: '苏晚', avatar: '苏', color: '#F472B6', type: 'TEXT', content: '在吗？想跟你确认一下明天的会议时间', time: '14:20' },
    { id: 'm2', conversationId: 'c_1', sender: 'self', type: 'TEXT', content: '在的，明天上午 10 点准时开始', time: '14:22' },
    { id: 'm3', conversationId: 'c_1', sender: 'other', name: '苏晚', avatar: '苏', color: '#F472B6', type: 'TEXT', content: '好的，地点还是会议室 A 吗？', time: '14:25' },
    { id: 'm4', conversationId: 'c_1', sender: 'self', type: 'TEXT', content: '对，会议室 A。我会提前把材料发到群里', time: '14:28' },
    { id: 'm5', conversationId: 'c_1', sender: 'other', name: '苏晚', avatar: '苏', color: '#F472B6', type: 'TEXT', content: '好的，明天见！', time: '14:32' }
  ],
  c_2: [
    { id: 'g1', conversationId: 'c_2', sender: 'system', type: 'SYSTEM', content: '林深 邀请 陈墨 加入群聊', time: '13:50' },
    { id: 'g2', conversationId: 'c_2', sender: 'other', name: '陈墨', avatar: '陈', color: '#10B981', type: 'TEXT', content: '大家下午好，会议要点已经整理在文档里了', time: '13:55' },
    { id: 'g3', conversationId: 'c_2', sender: 'other', name: '苏晚', avatar: '苏', color: '#F472B6', type: 'TEXT', content: '辛苦了！我看了一下，关于用户调研那部分想补充一些数据', time: '14:02' },
    { id: 'g4', conversationId: 'c_2', sender: 'self', type: 'TEXT', content: '@AI 帮我总结下今天的会议要点', time: '14:18' },
    { id: 'g5', conversationId: 'c_2', sender: 'ai', type: 'AI', content: '正在为您总结今天的会议要点：\n\n1. **用户调研补充**：苏晚提出需要在用户调研部分补充更多数据支持；\n2. **会议时间确认**：明天上午 10 点会议室 A 召开产品评审会；\n3. **文档同步**：陈墨已整理会议文档并同步至群组。\n\n需要我帮你生成一份完整的会议纪要吗？', time: '14:18' }
  ],
  c_3: [
    { id: 'p1', conversationId: 'c_3', sender: 'other', name: '陈墨', avatar: '陈', color: '#10B981', type: 'TEXT', content: '看看这个设计稿', time: '13:40' },
    { id: 'p2', conversationId: 'c_3', sender: 'other', name: '陈墨', avatar: '陈', color: '#10B981', type: 'IMAGE', content: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20minimal%20dashboard%20UI%20design%20blue%20white%20clean&image_size=landscape_4_3', time: '13:45' },
    { id: 'p3', conversationId: 'c_3', sender: 'self', type: 'TEXT', content: '颜色搭配很舒服，这个方向可以继续深入', time: '13:48' }
  ],
  c_5: [
    { id: 'a1', conversationId: 'c_5', sender: 'ai', type: 'AI', content: '你好，我是 ChatVibe AI 助手 ✨\n你可以随时 @我 或直接发消息，我会帮你处理工作事务、生成文案、总结内容等。', time: '昨天' },
    { id: 'a2', conversationId: 'c_5', sender: 'self', type: 'TEXT', content: '帮我写一段产品发布文案', time: '昨天' },
    { id: 'a3', conversationId: 'c_5', sender: 'ai', type: 'AI', content: '好的，这是一段产品发布文案草稿：\n\n**ChatVibe · 让沟通更有温度**\n\n融合真人实时对话与 AI 智能助手，在一个界面里完成所有协作。\n\n· 私聊 / 群聊，畅聊无界\n· @AI 召唤，即问即答\n· 智能总结，效率翻倍\n\n需要调整语气或重点吗？', time: '昨天' }
  ]
}

/** 默认消息（未配置的会话） */
export const DEFAULT_MESSAGES: Message[] = [
  { id: 'd1', conversationId: '', sender: 'system', type: 'SYSTEM', content: '开始对话', time: '现在' },
  { id: 'd2', conversationId: '', sender: 'other', name: '对方', avatar: 'U', color: '#94A3B8', type: 'TEXT', content: '你好，这是一条示例消息', time: '现在' }
]

/** 群成员（按会话 ID 分组） */
export const MOCK_GROUP_MEMBERS: Record<string, GroupMember[]> = {
  c_2: [
    { id: 'u1', name: '林深', avatar: '林', color: '#2563EB', role: 'owner', online: true },
    { id: 'u2', name: '苏晚', avatar: '苏', color: '#F472B6', role: 'member', online: true },
    { id: 'u3', name: '陈墨', avatar: '陈', color: '#10B981', role: 'member', online: true },
    { id: 'u4', name: '周野', avatar: '周', color: '#F59E0B', role: 'member', online: false },
    { id: 'u5', name: '老王', avatar: '王', color: '#7C3AED', role: 'member', online: false },
    { id: 'u6', name: '小鹿', avatar: '鹿', color: '#0EA5E9', role: 'member', online: true },
    { id: 'u7', name: '阿月', avatar: '月', color: '#EF4444', role: 'member', online: false },
    { id: 'u8', name: 'AI 助手', avatar: 'AI', color: '#7C3AED', role: 'AI', online: true, isAI: true }
  ],
  c_4: [
    { id: 'u1', name: '林深', avatar: '林', color: '#2563EB', role: 'owner', online: true },
    { id: 'u5', name: '老王', avatar: '王', color: '#7C3AED', role: 'member', online: false },
    { id: 'u6', name: '小鹿', avatar: '鹿', color: '#0EA5E9', role: 'member', online: true },
    { id: 'u9', name: '阿明', avatar: '明', color: '#DB2777', role: 'member', online: false },
    { id: 'u10', name: '小雨', avatar: '雨', color: '#0891B2', role: 'member', online: true }
  ],
  c_7: [
    { id: 'u1', name: '林深', avatar: '林', color: '#2563EB', role: 'owner', online: true },
    { id: 'u2', name: '苏晚', avatar: '苏', color: '#F472B6', role: 'member', online: true },
    { id: 'u11', name: '书友A', avatar: 'A', color: '#16A34A', role: 'member', online: false },
    { id: 'u12', name: '书友B', avatar: 'B', color: '#CA8A04', role: 'member', online: false }
  ]
}

/** 好友请求 - 待处理（收到的） */
export const MOCK_PENDING_REQUESTS: FriendRequest[] = [
  { id: 'r1', name: '夏目', avatar: '夏', color: '#10B981', info: '来自邮箱搜索 · 2 小时前', status: 'pending' },
  { id: 'r2', name: '阿月', avatar: '月', color: '#EF4444', info: '共同好友：陈墨 · 昨天', status: 'pending' }
]

/** 好友请求 - 已发送 */
export const MOCK_SENT_REQUESTS: FriendRequest[] = [
  { id: 'r3', name: '陆离', avatar: '陆', color: '#7C3AED', info: '已发送 · 等待验证', status: 'pending' },
  { id: 'r4', name: '远舟', avatar: '远', color: '#2563EB', info: '已发送 · 3 天前', status: 'pending' }
]

/** 我的好友列表 */
export const MOCK_FRIENDS: Friend[] = [
  { id: 'f1', name: '苏晚', avatar: '苏', color: '#F472B6', online: true, signature: '产品设计师', email: 'suwan@chatvibe.com' },
  { id: 'f2', name: '陈墨', avatar: '陈', color: '#10B981', online: true, signature: '前端工程师', email: 'chenmo@chatvibe.com' },
  { id: 'f3', name: '周野', avatar: '周', color: '#F59E0B', online: false, signature: '后端工程师', email: 'zhouye@chatvibe.com' },
  { id: 'f4', name: '夏目', avatar: '夏', color: '#10B981', online: true, signature: 'UI 设计师', email: 'xiamu@chatvibe.com' },
  { id: 'f5', name: '阿月', avatar: '月', color: '#EF4444', online: false, signature: '测试工程师', email: 'ayue@chatvibe.com' },
  { id: 'f6', name: '小鹿', avatar: '鹿', color: '#0EA5E9', online: true, signature: '运营专员', email: 'xiaolu@chatvibe.com' },
  { id: 'f7', name: '老王', avatar: '王', color: '#7C3AED', online: false, signature: '架构师', email: 'laowang@chatvibe.com' }
]

/** 好友 → 会话 ID 映射（用于点击好友跳转聊天） */
export const FRIEND_CONV_MAP: Record<string, string> = {
  f1: 'c_1',
  f2: 'c_3',
  f3: 'c_6'
}

/** AI 模拟回复内容 */
export const AI_REPLIES: string[] = [
  '收到你的问题 ✨ 让我来帮你分析一下：\n\n根据你的描述，建议从以下几个角度入手：\n1. 明确核心目标与边界条件\n2. 拆解关键步骤，按优先级执行\n3. 设置阶段性检查点，及时调整\n\n需要我针对某一点展开说明吗？',
  '好的，我已为你整理好要点：\n\n· 关键信息一：聚焦用户核心诉求\n· 关键信息二：保持信息流的连贯与一致\n· 关键信息三：在交互细节处体现产品温度\n\n希望对你有帮助，可以继续追问细节～',
  '这是一个很有意思的问题。我的思考如下：\n\n从产品角度看，需要平衡**功能完整性**与**使用门槛**；从用户角度看，要让 AI 真正融入自然对话流，而不是割裂的工具。\n\nChatVibe 的设计正是基于这个理念——让 AI 嵌入沟通，而非打断沟通。'
]
