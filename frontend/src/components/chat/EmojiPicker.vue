<script setup lang="ts">
// 微信风格表情选择器
// 分 6 大类：表情/手势/人物/动物/美食/符号
import { ref } from 'vue'

const emit = defineEmits<{
  (e: 'select', emoji: string): void
  (e: 'close'): void
}>()

interface EmojiCategory {
  icon: string
  name: string
  emojis: string[]
}

const categories: EmojiCategory[] = [
  {
    icon: '😀',
    name: '表情',
    emojis: [
      '😀','😃','😄','😁','😆','😅','😂','🤣',
      '😊','😇','🙂','🙃','😉','😌','😍','🥰',
      '😘','😗','😙','😚','😋','😛','😝','😜',
      '🤪','🤨','🧐','🤓','😎','🤩','🥳','😏',
      '😒','😞','😔','😟','😕','🙁','☹️','😣',
      '😖','😫','😩','🥺','😢','😭','😤','😠',
      '😡','🤬','🤯','😳','🥵','🥶','😱','😨',
      '😰','😥','😓','🤗','🤔','🤭','🤫','🤥',
      '😶','😐','😑','😬','🙄','😯','😦','😧',
      '😮','😲','🥱','😴','🤤','😪','😵','🤐',
      '🥴','🤢','🤮','🤧','😷','🤒','🤕','🤑',
      '🤠','😈','👿','👹','👺','🤡','💩','👻',
      '💀','☠️','👽','🤖','🎃','😺','😸','😹',
      '😻','😼','😽','🙀','😿','😾','🤲','👐'
    ]
  },
  {
    icon: '👋',
    name: '手势',
    emojis: [
      '👋','🤚','🖐','✋','🖖','👌','🤌','🤏',
      '✌️','🤞','🤟','🤘','🤙','👈','👉','👆',
      '🖕','👇','☝️','👍','👎','✊','👊','🤛',
      '🤜','👏','🙌','👐','🤲','🤝','🙏','✍️',
      '💪','🦾','🦿','🦵','🦶','👂','🦻','👃',
      '🧠','🦷','🦴','👀','👁','👅','👄','💋',
      '🤳','💅','🦳','🦰','🦱','🦲','👱','👨',
      '👩','🧑','👴','👵','👶','🧒','👦','👧',
      '🧔','👨‍🦰','👩‍🦰','👨‍🦱','👩‍🦱','👨‍🦳','👩‍🦳','👨‍🦲',
      '👩‍🦲','🧓','👴','👵','🙍','🙎','🙅','🙆',
      '💁','🙋','🧏','🙇','🤦','🤷','💆','💇',
      '🚶','🧍','🧎','🏃','💃','🕺','👯','🧖',
      '🧗','🧘','🛀','🛌','👩‍🚀','👨‍🚀','👩‍✈️','👨‍✈️'
    ]
  },
  {
    icon: '🐶',
    name: '动物',
    emojis: [
      '🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼',
      '🐨','🐯','🦁','🐮','🐷','🐽','🐸','🐵',
      '🙈','🙉','🙊','🐒','🐔','🐧','🐦','🐤',
      '🐣','🐥','🦆','🦅','🦉','🦇','🐺','🐗',
      '🐴','🦄','🐝','🐛','🦋','🐌','🐞','🐜',
      '🦟','🦗','🕷','🕸','🦂','🐢','🐍','🦎',
      '🦖','🦕','🐙','🦑','🦐','🦞','🦀','🐡',
      '🐠','🐟','🐬','🐳','🐋','🦈','🐊','🐅',
      '🐆','🦓','🦍','🦧','🐘','🦛','🦏','🐪',
      '🐫','🦒','🦘','🦬','🐃','🐂','🐄','🐎',
      '🐖','🐏','🐑','🦙','🐐','🦌','🐕','🐩',
      '🦕','🦖','🐇','🦝','🦨','🦡','🦫','🦦',
      '🦥','🐁','🐀','🐿','🦔','🐾','🐉','🐲'
    ]
  },
  {
    icon: '🍎',
    name: '美食',
    emojis: [
      '🍏','🍎','🍐','🍊','🍋','🍌','🍉','🍇',
      '🍓','🫐','🍈','🍒','🍑','🥭','🍍','🥥',
      '🥝','🍅','🍆','🥑','🥦','🥬','🥒','🌶',
      '🫑','🌽','🥕','🫒','🧄','🧅','🥔','🍠',
      '🥐','🥯','🍞','🥖','🥨','🧀','🥚','🍳',
      '🧈','🥞','🧇','🥓','🥩','🍗','🍖','🦴',
      '🌭','🍔','🍟','🍕','🥪','🥙','🧆','🌮',
      '🌯','🫔','🥗','🥘','🫕','🥫','🍝','🍜',
      '🍲','🍛','🍣','🍱','🥟','🦪','🍤','🍙',
      '🍚','🍘','🍥','🥠','🥮','🍢','🍡','🍧',
      '🍨','🍦','🥧','🧁','🍰','🎂','🍮','🍭',
      '🍬','🍫','🍩','🍪','🌰','🥜','🍯','🥛',
      '☕','🍵','🧃','🥤','🍶','🍺','🍻','🥂',
      '🍷','🥃','🍸','🍹','🧉','🍾','🧊','🥄'
    ]
  },
  {
    icon: '⚽',
    name: '活动',
    emojis: [
      '⚽','🏀','🏈','⚾','🥎','🎾','🏐','🏉',
      '🥏','🎱','🪀','🏓','🏸','🏒','🏑','🥍',
      '🏏','🪃','🥅','⛳','🪁','🎣','🤿','🧗',
      '🪂','🏋️','🤼','🤸','⛹️','🤺','🤾','🏌️',
      '🏇','🧘','🏄','🏊','🤽','🚣','🧗','🚴',
      '🚵','🤹','🎨','🎬','🎤','🎧','🎼','🎹',
      '🥁','🎷','🎺','🎸','🪕','🎻','🎲','♟',
      '🎯','🎳','🎮','🎰','🧩','🚗','🚕','🚙',
      '🚌','🚎','🏎','🚓','🚑','🚒','🚐','🛻',
      '🚚','🚛','🚜','🦯','🦽','🦼','🛴','🚲',
      '🛵','🏍','🛺','🚔','🚍','🚘','🚖','🚡',
      '🚠','🚟','🚃','🚋','🚞','🚝','🚄','🚅',
      '🚈','🚂','🚆','🚇','🚊','🚉','✈️','🛫'
    ]
  },
  {
    icon: '❤️',
    name: '符号',
    emojis: [
      '❤️','🧡','💛','💚','💙','💜','🖤','🤍',
      '🤎','💔','❣️','💕','💞','💓','💗','💖',
      '💘','💝','💟','☮️','✝️','☪️','🕉','☸️',
      '✡️','🔯','🕎','☯️','☦️','🛐','⛎','♈',
      '♉','♊','♋','♌','♍','♎','♏','♐',
      '♑','♒','♓','🆔','⚛️','🉑','☢️','☣️',
      '📴','📳','🈶','🈚','🈸','🈺','🈷️','✴️',
      '🆚','🉹','🈲','🅰️','🅱️','🆎','🆑','🅾️',
      '🆘','❌','⭕','🛑','⛔','📛','🚫','💯',
      '💢','♨️','🚷','🚯','🚳','🚱','🔞','📵',
      '🚭','❗','❕','❓','❔','‼️','⁉️','🔅',
      '🔆','〽️','⚠️','🚸','🔱','⚜️','🔰','♻️',
      '✅','🈯','💹','❇️','✳️','❎','🌐','Ⓜ️',
      '💠','🌀','💤','🏧','🚾','♿','🅿️','🈳'
    ]
  }
]

const activeIndex = ref(0)

function selectEmoji(emoji: string) {
  emit('select', emoji)
}

function switchCategory(index: number) {
  activeIndex.value = index
}
</script>

<template>
  <div class="emoji-picker">
    <!-- 表情网格 -->
    <div class="emoji-grid">
      <button
        v-for="(emoji, i) in categories[activeIndex].emojis"
        :key="`${activeIndex}-${i}`"
        class="emoji-item"
        @click="selectEmoji(emoji)"
      >
        {{ emoji }}
      </button>
    </div>

    <!-- 分类标签栏 -->
    <div class="emoji-tabs">
      <button
        v-for="(cat, i) in categories"
        :key="cat.name"
        class="emoji-tab"
        :class="{ active: activeIndex === i }"
        :title="cat.name"
        @click="switchCategory(i)"
      >
        {{ cat.icon }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.emoji-picker {
  width: 360px;
  height: 280px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.16);
}

.emoji-grid {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
}

.emoji-grid::-webkit-scrollbar {
  width: 6px;
}

.emoji-grid::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 22px;
  cursor: pointer;
  transition: background 0.15s;
  line-height: 1;
}

.emoji-item:hover {
  background: rgba(0, 0, 0, 0.06);
}

.emoji-tabs {
  display: flex;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  padding: 4px 8px;
  gap: 4px;
}

.emoji-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 20px;
  cursor: pointer;
  transition: background 0.15s;
  line-height: 1;
}

.emoji-tab:hover {
  background: rgba(0, 0, 0, 0.05);
}

.emoji-tab.active {
  background: rgba(37, 99, 235, 0.12);
}
</style>
