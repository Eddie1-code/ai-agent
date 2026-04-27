<template>
  <div class="landing-page">
    <header class="topbar section-shell panel">
      <a class="brand" href="#hero">{{ brandCopy.brandName }}</a>
      <nav class="top-nav">
        <a href="#name">Name</a>
        <a href="#about">About</a>
        <a href="#skills">Skills</a>
        <a href="#projects">Projects</a>
      </nav>
    </header>

    <main>
      <section id="hero" class="section-shell hero-section" ref="heroRef">
        <div class="hero-stage panel" :style="heroStyleVars">
          <div class="hero-sticker">
            <p class="hero-sticker__hello">HELLO</p>
            <p class="hero-sticker__name">my mentor is</p>
            <p
              class="hero-sticker__sign"
              :style="{
                '--ink-shift-x': `${inkShift.x}px`,
                '--ink-shift-y': `${inkShift.y}px`,
                '--text-shift-x': `${-inkShift.x * 0.38}px`,
                '--text-shift-y': `${-inkShift.y * 0.38}px`
              }"
              @mousemove="onInkMove"
              @mouseleave="resetInkMove"
            >
              <span class="hero-sticker__ink" aria-hidden="true"></span>
              <span class="hero-sticker__ink-texture" aria-hidden="true"></span>
              <span class="hero-sticker__sign-text hero-sticker__sign-text--top">AI Life</span>
              <span class="hero-sticker__sign-text hero-sticker__sign-text--bottom">Mentor</span>
            </p>
            <span class="hero-sticker__peel" aria-hidden="true"></span>
          </div>
          <div class="hero-copy-wrap">
            <p class="issue-tag">{{ brandCopy.heroIssue }}</p>
            <h1>{{ brandCopy.heroTitle }}</h1>
            <p class="hero-copy">{{ brandCopy.heroDescription }}</p>
            <div class="hero-actions">
              <button class="btn-pill btn-pill--primary" @click="navigateTo('/chat?mode=coach')">{{ brandCopy.heroPrimaryCta }}</button>
              <button class="btn-pill" @click="navigateTo('/chat?mode=planner')">{{ brandCopy.heroSecondaryCta }}</button>
            </div>
          </div>
        </div>
      </section>

      <section
        id="name"
        ref="nameSectionRef"
        class="section-shell issue-section panel name-section"
        :style="{ '--fold': sectionFold.name.toFixed(3), '--name-progress': nameProgress.toFixed(3) }"
      >
        <div class="section-heading section-heading--split">
          <div>
            <p class="eyebrow">Issue 02 / Name Deconstruction</p>
            <h2>MENTORVERSE</h2>
          </div>
          <p class="section-summary">AI 生活导师，聚焦你的决策、行动和复盘闭环。</p>
        </div>

        <div class="name-poster">
          <div class="name-ghost">
            <span
              v-for="(item, index) in nameRows"
              :key="`ghost-${item.key}-${index}`"
              :style="{ '--drop-progress': calcNameDropProgress(index).toFixed(3) }"
            >{{ item.key }}</span>
          </div>
          <div class="name-stack">
            <article
              v-for="(item, index) in nameRows"
              :key="`${item.key}-${item.word}`"
              class="name-row"
              :class="{ 'is-active': activeNameIndex === index }"
              :style="{ '--drop-progress': calcNameDropProgress(index).toFixed(3) }"
              @mouseenter="activeNameIndex = index"
              @mouseleave="activeNameIndex = -1"
            >
              <span class="name-letter-shell">
                <span class="name-letter">{{ item.key }}</span>
              </span>
              <div class="name-copy">
                <em>{{ item.word }}</em>
                <small>{{ item.desc }}</small>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section
        id="about"
        ref="aboutSectionRef"
        class="section-shell issue-section about-section"
        :style="aboutStyleVars"
      >
        <div class="section-heading">
          <p class="eyebrow">{{ brandCopy.about.title }}</p>
          <h2>ABOUT THIS MENTOR</h2>
        </div>
        <div class="about-layout">
          <article class="panel about-card">
            <p>{{ brandCopy.about.summary }}</p>
            <div class="issue-divider"></div>
            <p>{{ brandCopy.sections.positionBody }}</p>
            <div class="about-metrics">
              <article v-for="item in aboutMetrics" :key="item.label" class="metric-chip">
                <strong>{{ item.value }}</strong>
                <small>{{ item.label }}</small>
              </article>
            </div>
          </article>
          <aside class="panel assistant-card">
            <p class="eyebrow">Ask The Mentor</p>
            <p class="assistant-answer">“你可以只说一句话：我现在很乱。剩下的拆解和路径，我来给你。”</p>
            <div class="assistant-flow">
              <div v-for="step in mentorFlow" :key="step.title" class="flow-row">
                <span>{{ step.title }}</span>
                <small>{{ step.desc }}</small>
              </div>
            </div>
          </aside>
        </div>
        <div class="about-details">
          <article v-for="block in aboutBlocks" :key="block.title" class="panel detail-card">
            <p class="eyebrow">{{ block.kicker }}</p>
            <h3>{{ block.title }}</h3>
            <p>{{ block.body }}</p>
          </article>
        </div>
      </section>

      <section
        id="skills"
        ref="skillsSectionRef"
        class="section-shell issue-section skills-section"
        :style="{ '--fold': sectionFold.skills.toFixed(3), '--web-progress': skillsProgress.toFixed(3) }"
      >
        <div class="skills-stage panel">
          <div class="section-heading">
            <p class="eyebrow">{{ brandCopy.capability.title }}</p>
            <h2>SKILL UNIVERSE</h2>
          </div>
          <div class="skills-web-shell" aria-hidden="true">
            <span class="web-core"></span>
            <span class="web-lines web-lines--ring"></span>
            <span class="web-lines web-lines--radial"></span>
            <span class="web-glow"></span>
          </div>
          <div class="skills-grid">
            <article class="panel skill-card" :style="{ '--card-progress': calcCardProgress(0).toFixed(3) }">
              <h3>状态解读</h3>
              <p>从你当前情绪、精力与目标冲突中抽取关键变量，先判断“卡点是什么”。</p>
            </article>
            <article class="panel skill-card" :style="{ '--card-progress': calcCardProgress(1).toFixed(3) }">
              <h3>行动计划</h3>
              <p>把抽象目标拆成日程、步骤和里程碑，给出今天就能执行的下一步。</p>
            </article>
            <article class="panel skill-card" :style="{ '--card-progress': calcCardProgress(2).toFixed(3) }">
              <h3>复盘修正</h3>
              <p>记录结果并识别偏差，自动生成下一轮调整建议，形成持续迭代闭环。</p>
            </article>
            <article class="panel skill-card" :style="{ '--card-progress': calcCardProgress(3).toFixed(3) }">
              <h3>多场景支持</h3>
              <p>学习、职业、健康、人际等问题统一管理，提供个性化长期陪跑策略。</p>
            </article>
          </div>
        </div>
      </section>

      <section
        id="projects"
        ref="projectsSectionRef"
        class="section-shell issue-section projects-section"
        :style="{
          '--fold': sectionFold.projects.toFixed(3),
          '--projects-progress': projectsProgress.toFixed(3),
          '--projects-fan-progress': projectsFanProgress.toFixed(3)
        }"
      >
        <div class="section-heading">
          <p class="eyebrow">{{ brandCopy.projects.title }}</p>
          <h2>SELECTED PROJECTS</h2>
        </div>
        <div class="project-grid">
          <article
            v-for="(card, index) in brandCopy.projects.cards"
            :key="card.idx"
            class="panel project-card"
            :class="`project-card--${index + 1}`"
            :style="projectFanStyle(index)"
          >
            <div class="project-cover">
              <span class="project-logo">{{ projectLogos[index] }}</span>
              <span class="project-impact">{{ card.impact }}</span>
              <span class="project-burst" aria-hidden="true"></span>
              <span class="project-rays" aria-hidden="true"></span>
            </div>
            <span class="project-index">{{ card.idx }}</span>
            <h3>{{ card.title }}</h3>
            <p class="project-type">{{ card.type }}</p>
            <p class="project-body">{{ card.body }}</p>
          </article>
        </div>
      </section>

      <section
        id="contact"
        ref="contactSectionRef"
        class="section-shell issue-section contact-section"
        :style="contactStyleVars"
      >
        <div class="panel contact-panel">
          <p class="eyebrow">{{ brandCopy.contact.title }}</p>
          <h2 class="contact-title">
            <span class="contact-title-line contact-title-line--left" :style="{ '--line-progress': calcContactLineProgress(0).toFixed(3) }">LET'S BUILD A</span>
            <span class="contact-title-line contact-title-line--right" :style="{ '--line-progress': calcContactLineProgress(1).toFixed(3) }">BETTER LIFE</span>
            <span class="contact-title-line contact-title-line--left" :style="{ '--line-progress': calcContactLineProgress(2).toFixed(3) }">LOOP.</span>
          </h2>
          <p>{{ brandCopy.contact.copy }}</p>
          <button class="btn-pill btn-pill--primary contact-cta-btn" @click="navigateTo('/chat?mode=coach')">{{ brandCopy.contact.cta }}</button>
        </div>
      </section>
    </main>

    <div class="section-shell">
      <AppFooter />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppFooter from '../components/AppFooter.vue'
import { brandCopy } from '../constants/copy'

useHead({
  title: '个性化AI生活导师',
  meta: [{ name: 'description', content: '个性化AI生活导师，为你的学习、职业、生活提供长期可执行的成长路径。' }]
})

const router = useRouter()
const activeNameIndex = ref(-1)
const heroProgress = ref(0)
const heroProgressTarget = ref(0)
const nameProgress = ref(0)
const nameProgressTarget = ref(0)
const skillsProgress = ref(0)
const projectsProgress = ref(0)
const projectsFanProgress = ref(0)
const aboutProgress = ref(0)
const contactProgress = ref(0)
const contactProgressTarget = ref(0)
const inkShift = ref({ x: 0, y: 0 })
let smoothRaf = 0
let lastFrameTime = 0

const nameSectionRef = ref(null)
const aboutSectionRef = ref(null)
const skillsSectionRef = ref(null)
const projectsSectionRef = ref(null)
const contactSectionRef = ref(null)
const heroRef = ref(null)

const sectionFold = ref({
  name: 0,
  about: 0,
  skills: 0,
  projects: 0,
  contact: 0
})

const nameRows = [
  { key: 'M', word: 'Mindful', desc: '先看见你的真实状态' },
  { key: 'E', word: 'Evidence-based', desc: '建议有依据，不空泛' },
  { key: 'N', word: 'Navigated', desc: '复杂目标拆成可走路径' },
  { key: 'T', word: 'Trackable', desc: '每一步都可追踪和复盘' },
  { key: 'O', word: 'Optimized', desc: '持续优化你的行动效率' },
  { key: 'R', word: 'Reliable', desc: '稳定、长期、可信的陪跑' },
  { key: 'V', word: 'Visioned', desc: '兼顾当下执行与长期愿景' },
  { key: 'E', word: 'Empathetic', desc: '专业判断 + 温暖表达' },
  { key: 'R', word: 'Result-driven', desc: '最终回到真实改变' },
  { key: 'S', word: 'Structured', desc: '把混乱整理成结构' },
  { key: 'E', word: 'Evolving', desc: '根据结果不断进化策略' }
]

const projectLogos = ['⌘', 'Fut.', '▣', 'SM']
const projectFanPresets = [
  { x: -72, y: 34, rot: -16, z: 5, dim: 0.2, scale: -0.03 },
  { x: -34, y: 20, rot: -6, z: 6, dim: 0.1, scale: 0.0 },
  { x: 44, y: 24, rot: 7, z: 7, dim: 0.13, scale: 0.01 },
  { x: 102, y: 8, rot: 15, z: 4, dim: 0.22, scale: -0.04 }
]
const aboutMetrics = [
  { label: '目标拆解维度', value: '4D' },
  { label: '日计划颗粒度', value: '15min' },
  { label: '复盘周期', value: '7d' }
]

const mentorFlow = [
  { title: '状态扫描', desc: '识别情绪、精力、注意力和时间约束。' },
  { title: '路径规划', desc: '输出主线任务、备选方案和风险提醒。' },
  { title: '执行跟进', desc: '按日推进，记录偏差并动态修正策略。' }
]

const aboutBlocks = [
  {
    kicker: 'Method',
    title: '决策框架',
    body: '先厘清问题类型，再判断目标优先级，最后给出可执行路径，避免只给“看起来正确”的建议。'
  },
  {
    kicker: 'Boundary',
    title: '服务边界',
    body: '不替你做选择，但会把每个选项的成本、风险、收益讲清楚，让你在有限信息下做更稳的决定。'
  },
  {
    kicker: 'Scenario',
    title: '典型场景',
    body: '学习拖延、职业转向、作息失衡、人际压力等都可进入同一套成长系统，形成连续的行动轨迹。'
  }
]

const navigateTo = (path) => router.push(path)

const onInkMove = (event) => {
  const rect = event.currentTarget.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const offsetX = (event.clientX - centerX) / rect.width
  const offsetY = (event.clientY - centerY) / rect.height
  inkShift.value = {
    x: clamp(offsetX * 2.2, -1.2, 1.2),
    y: clamp(offsetY * 2.2, -1.2, 1.2)
  }
}

const resetInkMove = () => {
  inkShift.value = { x: 0, y: 0 }
}

const clamp = (value, min, max) => Math.min(Math.max(value, min), max)

const calcFold = (el) => {
  if (!el) return 0
  const rect = el.getBoundingClientRect()
  const start = window.innerHeight * 0.95
  const end = window.innerHeight * 0.2
  const distance = start - end
  const progress = distance > 0 ? clamp((start - rect.top) / distance, 0, 1) : 0
  return progress
}

const calcCardProgress = (index) => {
  const start = index * 0.12
  const end = start + 0.34
  return clamp((skillsProgress.value - start) / (end - start), 0, 1)
}

const calcNameDropProgress = (index) => {
  const start = index * 0.07
  const end = start + 0.3
  return clamp((nameProgress.value - start) / (end - start), 0, 1)
}

const calcPhaseProgress = (progress, start, end) => {
  if (end <= start) return 0
  return clamp((progress - start) / (end - start), 0, 1)
}

const calcContactLineProgress = (index) => {
  const start = 0.04 + index * 0.14
  const end = start + 0.38
  return clamp((contactProgress.value - start) / (end - start), 0, 1)
}

const aboutStyleVars = computed(() => {
  const p = aboutProgress.value
  const heading = calcPhaseProgress(p, 0.04, 0.28)
  const layout = calcPhaseProgress(p, 0.22, 0.62)
  const details = calcPhaseProgress(p, 0.52, 0.95)
  return {
    '--fold': sectionFold.value.about.toFixed(3),
    '--about-progress': p.toFixed(3),
    '--about-heading-progress': heading.toFixed(3),
    '--about-layout-progress': layout.toFixed(3),
    '--about-details-progress': details.toFixed(3)
  }
})

const heroStyleVars = computed(() => ({
  '--hero-progress': heroProgress.value.toFixed(3),
  '--hero-clarity-progress': calcPhaseProgress(1 - heroProgress.value, 0.04, 0.9).toFixed(3),
  '--hero-copy-progress': calcPhaseProgress(1 - heroProgress.value, 0.08, 0.95).toFixed(3),
  '--hero-card-progress': calcPhaseProgress(1 - heroProgress.value, 0.02, 0.9).toFixed(3)
}))

const contactStyleVars = computed(() => {
  const p = contactProgress.value
  return {
    '--fold': sectionFold.value.contact.toFixed(3),
    '--contact-progress': p.toFixed(3),
    '--contact-title-progress': calcPhaseProgress(p, 0.02, 0.42).toFixed(3),
    '--contact-copy-progress': calcPhaseProgress(p, 0.3, 0.72).toFixed(3),
    '--contact-cta-progress': calcPhaseProgress(p, 0.58, 0.98).toFixed(3)
  }
})

const projectFanStyle = (index) => {
  const preset = projectFanPresets[index] || projectFanPresets[1]
  return {
    '--fan-x': `${preset.x}px`,
    '--fan-y': `${preset.y}px`,
    '--fan-rot': `${preset.rot}deg`,
    '--fan-z': preset.z,
    '--fan-dim': preset.dim,
    '--fan-scale': preset.scale
  }
}

const onScroll = () => {
  const max = document.body.scrollHeight - window.innerHeight
  const progress = max > 0 ? window.scrollY / max : 0
  document.documentElement.style.setProperty('--home-progress', progress.toFixed(3))

  const heroRect = heroRef.value?.getBoundingClientRect()
  if (heroRect) {
    const total = Math.max(heroRect.height - window.innerHeight * 0.2, 1)
    heroProgressTarget.value = clamp((window.innerHeight * 0.12 - heroRect.top) / total, 0, 1)
  }

  sectionFold.value = {
    name: calcFold(nameSectionRef.value),
    about: calcFold(aboutSectionRef.value),
    skills: calcFold(skillsSectionRef.value),
    projects: calcFold(projectsSectionRef.value),
    contact: calcFold(contactSectionRef.value)
  }

  const nameRect = nameSectionRef.value?.getBoundingClientRect()
  if (nameRect) {
    const start = window.innerHeight * 0.94
    const end = -nameRect.height * 0.2
    const travel = start - end
    const progress = travel > 0 ? clamp((start - nameRect.top) / travel, 0, 1) : 0
    nameProgressTarget.value = progress
  }

  const skillsRect = skillsSectionRef.value?.getBoundingClientRect()
  if (skillsRect) {
    const start = window.innerHeight * 0.85
    const end = -skillsRect.height * 0.45
    const travel = start - end
    const value = travel > 0 ? clamp((start - skillsRect.top) / travel, 0, 1) : 0
    skillsProgress.value = value
  }

  const aboutRect = aboutSectionRef.value?.getBoundingClientRect()
  if (aboutRect) {
    const start = window.innerHeight * 0.9
    const end = -aboutRect.height * 0.32
    const travel = start - end
    const progress = travel > 0 ? clamp((start - aboutRect.top) / travel, 0, 1) : 0
    aboutProgress.value = progress
  }

  const projectsRect = projectsSectionRef.value?.getBoundingClientRect()
  if (projectsRect) {
    const start = window.innerHeight * 0.96
    const end = -projectsRect.height * 0.54
    const travel = start - end
    const progress = travel > 0 ? clamp((start - projectsRect.top) / travel, 0, 1) : 0
    projectsProgress.value = progress
    projectsFanProgress.value = clamp((progress - 0.34) / 0.66, 0, 1)
  }

  const contactRect = contactSectionRef.value?.getBoundingClientRect()
  if (contactRect) {
    const start = window.innerHeight * 0.92
    const end = -contactRect.height * 0.32
    const travel = start - end
    const progress = travel > 0 ? clamp((start - contactRect.top) / travel, 0, 1) : 0
    contactProgressTarget.value = progress
  }
}

const smoothTick = (timestamp = performance.now()) => {
  const delta = lastFrameTime ? Math.min(timestamp - lastFrameTime, 40) : 16.67
  lastFrameTime = timestamp
  const baseEase = delta > 24 ? 0.085 : 0.13
  const ease = 1 - Math.pow(1 - baseEase, delta / 16.67)
  heroProgress.value += (heroProgressTarget.value - heroProgress.value) * ease
  nameProgress.value += (nameProgressTarget.value - nameProgress.value) * ease
  contactProgress.value += (contactProgressTarget.value - contactProgress.value) * ease

  if (Math.abs(heroProgressTarget.value - heroProgress.value) < 0.0005) {
    heroProgress.value = heroProgressTarget.value
  }
  if (Math.abs(contactProgressTarget.value - contactProgress.value) < 0.0005) {
    contactProgress.value = contactProgressTarget.value
  }
  if (Math.abs(nameProgressTarget.value - nameProgress.value) < 0.0005) {
    nameProgress.value = nameProgressTarget.value
  }
  smoothRaf = window.requestAnimationFrame(smoothTick)
}

onMounted(() => {
  onScroll()
  smoothTick()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(smoothRaf)
  lastFrameTime = 0
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
  padding: 10px 0 34px;
}

.topbar {
  position: sticky;
  top: 8px;
  z-index: 60;
  margin-bottom: 28px;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand {
  padding: 10px 14px;
  border: 1px solid var(--line-soft);
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.08em;
  font-weight: 800;
}

.top-nav {
  display: flex;
  gap: 10px;
}

.top-nav a {
  padding: 10px 14px;
  border: 1px solid var(--line-soft);
  border-radius: 999px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--ink-muted);
}

.issue-section {
  margin-bottom: 56px;
  transform: perspective(1200px) rotateX(calc((1 - var(--fold, 1)) * 10deg)) scale(calc(0.94 + var(--fold, 1) * 0.06));
  transform-origin: 50% 0%;
  opacity: calc(0.5 + var(--fold, 1) * 0.5);
  filter: blur(calc((1 - var(--fold, 1)) * 2px));
  transition: transform 0.14s linear, opacity 0.14s linear, filter 0.14s linear;
}

.hero-section {
  margin-bottom: 50px;
}

.hero-stage {
  min-height: 88vh;
  padding: 56px;
  display: grid;
  grid-template-columns: 0.98fr 1.08fr;
  gap: 40px;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.hero-stage::before {
  content: '';
  position: absolute;
  inset: -20% -8%;
  background:
    radial-gradient(circle at 18% 42%, rgba(255, 70, 86, 0.14), transparent 34%),
    radial-gradient(circle at 82% 48%, rgba(87, 141, 255, 0.2), transparent 38%);
  opacity: calc(0.18 + var(--hero-clarity-progress, 0) * 0.56);
  transform: translateY(calc((1 - var(--hero-clarity-progress, 0)) * 20px));
  transition: opacity 0.22s linear, transform 0.22s linear;
  pointer-events: none;
}

.hero-sticker {
  width: min(560px, 100%);
  margin: 0 auto;
  border-radius: 34px;
  border: 10px solid #ff3e4d;
  background: #f5f5f5;
  overflow: hidden;
  transform:
    translateY(calc((1 - var(--hero-card-progress, 0)) * 26px))
    rotate(calc(-4deg + var(--hero-progress) * 2.8deg))
    scale(calc(0.95 + var(--hero-card-progress, 0) * 0.05));
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.52);
  position: relative;
  filter: blur(calc((1 - var(--hero-card-progress, 0)) * 1.4px));
  transition: transform 0.2s linear, filter 0.2s linear;
}

.hero-sticker__hello {
  background: #ff3e4d;
  color: #fff;
  font-weight: 800;
  font-size: clamp(2rem, 5vw, 4.4rem);
  text-align: center;
  line-height: 1;
  padding: 22px 18px 12px;
}

.hero-sticker__name {
  background: #ff3e4d;
  color: #fff;
  text-align: center;
  font-weight: 700;
  padding-bottom: 18px;
  font-size: clamp(1rem, 1.8vw, 1.8rem);
}

.hero-sticker__sign {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 8px;
  padding: 24px 32px 30px;
  position: relative;
  overflow: hidden;
  animation: inkBreath 3.2s ease-in-out infinite;
}

.hero-sticker__ink {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 18% 24%, rgba(0, 0, 0, 0.24) 0 8px, transparent 9px),
    radial-gradient(circle at 52% 44%, rgba(0, 0, 0, 0.19) 0 10px, transparent 12px),
    radial-gradient(circle at 70% 32%, rgba(0, 0, 0, 0.18) 0 7px, transparent 8px),
    radial-gradient(circle at 40% 72%, rgba(0, 0, 0, 0.15) 0 6px, transparent 7px),
    radial-gradient(circle at 77% 76%, rgba(0, 0, 0, 0.15) 0 5px, transparent 6px),
    radial-gradient(80% 28% at 58% 56%, rgba(0, 0, 0, 0.12), transparent 70%),
    linear-gradient(10deg, transparent 70%, rgba(0, 0, 0, 0.16) 71% 72%, transparent 73%),
    linear-gradient(-6deg, transparent 77%, rgba(0, 0, 0, 0.13) 78% 79%, transparent 80%);
  opacity: 0.84;
  transform: translate(calc(var(--ink-shift-x, 0px) * 0.35), calc(var(--ink-shift-y, 0px) * 0.35));
  transition: transform 0.24s ease-out;
  pointer-events: none;
}

.hero-sticker__ink-texture {
  position: absolute;
  inset: 10px 10px 12px;
  background: url('../assets/ink-texture-mask.svg') center / 102% 102% no-repeat;
  mix-blend-mode: multiply;
  opacity: 0.38;
  transform: translate(var(--ink-shift-x, 0px), var(--ink-shift-y, 0px)) scale(1.01);
  transition: transform 0.24s ease-out;
  pointer-events: none;
}

.hero-sticker__sign-text {
  color: #151515;
  font-family: 'Bradley Hand', 'Segoe Script', 'Comic Sans MS', cursive;
  font-size: clamp(2.1rem, 4.2vw, 3.9rem);
  letter-spacing: 0.02em;
  font-weight: 700;
  font-style: italic;
  text-shadow:
    1px 0 rgba(18, 18, 18, 0.92),
    -1px 0 rgba(18, 18, 18, 0.9),
    0 0.8px rgba(18, 18, 18, 0.92),
    0 -0.8px rgba(18, 18, 18, 0.88),
    1.8px 1.8px 0 rgba(18, 18, 18, 0.24);
  position: relative;
  z-index: 1;
  isolation: isolate;
  transition: transform 0.24s ease-out;
}

.hero-sticker__sign-text::before {
  content: '';
  position: absolute;
  left: -4%;
  right: -8%;
  top: 48%;
  height: 12px;
  background: radial-gradient(ellipse at center, rgba(0, 0, 0, 0.2), transparent 72%);
  transform: rotate(-7deg);
  z-index: -1;
  opacity: 0.55;
}

.hero-sticker__sign-text::after {
  content: '';
  position: absolute;
  left: 6%;
  right: -6%;
  bottom: -6px;
  height: 14px;
  border-bottom: 2px solid rgba(20, 20, 20, 0.34);
  border-radius: 50%;
  transform: rotate(-6deg);
  opacity: 0.42;
  filter: blur(0.4px);
}

.hero-sticker__sign-text--top {
  transform: translate(var(--text-shift-x, 0px), var(--text-shift-y, 0px)) rotate(-7.6deg) translateX(-8px);
}

.hero-sticker__sign-text--bottom {
  transform: translate(var(--text-shift-x, 0px), var(--text-shift-y, 0px)) rotate(-2.4deg) translateX(34px);
}

@keyframes inkBreath {
  0%,
  100% {
    filter: brightness(0.99) contrast(1);
  }
  50% {
    filter: brightness(1.04) contrast(1.03);
  }
}

.hero-sticker__peel {
  position: absolute;
  right: 0;
  top: 0;
  width: calc(var(--hero-progress) * 36%);
  height: calc(var(--hero-progress) * 45%);
  background: linear-gradient(135deg, #f0ede5, #d7d2c8);
  clip-path: polygon(100% 0, 0 100%, 100% 100%);
  border-left: 1px solid rgba(0, 0, 0, 0.08);
}

.hero-copy-wrap h1 {
  margin-top: 18px;
  max-width: 16ch;
  font-size: clamp(2.4rem, 4.4vw, 5.1rem);
  line-height: 1.08;
  letter-spacing: -0.015em;
  word-break: keep-all;
  transform: translateY(calc((1 - var(--hero-copy-progress, 0)) * 20px));
  opacity: calc(0.5 + var(--hero-copy-progress, 0) * 0.5);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.hero-copy {
  margin-top: 20px;
  max-width: 62ch;
  color: var(--ink-soft);
  line-height: 1.84;
  font-size: clamp(1rem, 1.25vw, 1.14rem);
  transform: translateY(calc((1 - var(--hero-copy-progress, 0)) * 16px));
  opacity: calc(0.45 + var(--hero-copy-progress, 0) * 0.55);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.hero-copy-wrap .issue-tag {
  display: inline-flex;
  align-items: center;
  height: 36px;
  border-radius: 999px;
  padding: 0 16px;
  font-size: 11px;
  letter-spacing: 0.12em;
  border: 1px solid rgba(255, 95, 95, 0.34);
  background: rgba(255, 95, 95, 0.08);
  transform: translateY(calc((1 - var(--hero-copy-progress, 0)) * 12px));
  opacity: calc(0.5 + var(--hero-copy-progress, 0) * 0.5);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.hero-actions {
  margin-top: 34px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  transform: translateY(calc((1 - var(--hero-copy-progress, 0)) * 22px));
  opacity: calc(0.5 + var(--hero-copy-progress, 0) * 0.5);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.section-heading--split {
  align-items: flex-start;
}

.section-summary {
  color: var(--ink-soft);
  max-width: 420px;
}

.name-section {
  padding: 40px;
  min-height: 86vh;
}

.name-poster {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 22px;
}

.name-ghost {
  display: grid;
  gap: 2px;
  align-content: start;
}

.name-ghost span {
  font-size: clamp(4rem, 7vw, 7rem);
  line-height: 0.92;
  font-weight: 800;
  color: rgba(245, 241, 232, calc(0.02 + var(--drop-progress, 0) * 0.08));
  transform: translateY(calc((1 - var(--drop-progress, 0)) * -52px));
  opacity: calc(0.12 + var(--drop-progress, 0) * 0.88);
  filter: blur(calc((1 - var(--drop-progress, 0)) * 2.2px));
  transition: transform 0.16s linear, opacity 0.16s linear, filter 0.16s linear, color 0.16s linear;
}

.name-stack {
  display: grid;
  gap: 10px;
}

.name-row {
  --active-shift: 0px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  border-radius: 14px;
  opacity: calc(0.18 + var(--drop-progress, 0) * 0.82);
  transform: translateY(calc((1 - var(--drop-progress, 0)) * -30px)) translateX(var(--active-shift));
  filter: blur(calc((1 - var(--drop-progress, 0)) * 2px));
  transition: transform 0.18s linear, opacity 0.18s linear, filter 0.18s linear, background 0.22s ease;
}

.name-row.is-active {
  --active-shift: 10px;
  background: rgba(255, 255, 255, 0.06);
}

.name-letter-shell {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  border: 1px solid var(--line-soft);
  display: grid;
  place-items: center;
  transition: transform 0.2s ease, background 0.2s ease;
}

.name-row.is-active .name-letter-shell {
  transform: rotate(-6deg) scale(1.08);
  background: linear-gradient(120deg, #ff4fcb, #8dff42);
}

.name-letter {
  font-size: 2.2rem;
  font-weight: 800;
}

.name-copy em {
  font-style: normal;
  font-size: 2rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.name-copy small {
  display: inline-block;
  margin-left: 10px;
  color: var(--ink-muted);
}

.about-section .section-heading h2 {
  font-size: clamp(2.3rem, 5vw, 5.4rem);
}

.about-layout {
  min-height: 58vh;
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 24px;
  margin-bottom: 16px;
  transform: translateY(calc((1 - var(--about-layout-progress, 1)) * 28px));
  opacity: calc(0.2 + var(--about-layout-progress, 1) * 0.8);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.about-card,
.assistant-card {
  padding: 24px;
}

.about-card {
  display: grid;
  align-content: start;
  gap: 12px;
}

.assistant-answer {
  margin-top: 12px;
  font-size: 1.05rem;
  line-height: 1.6;
}

.about-metrics {
  margin-top: 2px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-chip {
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.03);
  display: grid;
  gap: 4px;
}

.metric-chip strong {
  font-size: 1.18rem;
  color: #9ce6ff;
}

.metric-chip small {
  color: var(--ink-muted);
  font-size: 12px;
}

.assistant-flow {
  margin-top: 18px;
  display: grid;
  gap: 10px;
}

.flow-row {
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(8, 10, 16, 0.45);
}

.flow-row span {
  font-weight: 700;
  font-size: 0.95rem;
}

.flow-row small {
  display: block;
  margin-top: 4px;
  color: var(--ink-muted);
  line-height: 1.5;
}

.about-details {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  transform: translateY(calc((1 - var(--about-details-progress, 1)) * 42px));
  opacity: calc(0.08 + var(--about-details-progress, 1) * 0.92);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.detail-card {
  padding: 18px 18px 20px;
  transform: translateY(calc((1 - var(--about-details-progress, 1)) * 10px));
  transition: transform 0.2s linear;
}

.about-details .detail-card:nth-child(2) {
  transform: translateY(calc((1 - var(--about-details-progress, 1)) * 18px));
}

.about-details .detail-card:nth-child(3) {
  transform: translateY(calc((1 - var(--about-details-progress, 1)) * 26px));
}

.about-section .section-heading {
  transform: translateY(calc((1 - var(--about-heading-progress, 1)) * 16px));
  opacity: calc(0.2 + var(--about-heading-progress, 1) * 0.8);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.detail-card h3 {
  margin-top: 6px;
  margin-bottom: 8px;
  font-size: 1.22rem;
}

.detail-card p {
  color: var(--ink-soft);
  line-height: 1.66;
}

.skills-grid {
  min-height: 72vh;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
  padding: 22px;
  align-content: end;
  position: relative;
  z-index: 3;
}

.skills-stage {
  min-height: 180vh;
  padding: 26px;
  position: relative;
}

.skills-stage .section-heading {
  position: sticky;
  top: 22px;
  z-index: 4;
  margin-bottom: 18px;
}

.skills-web-shell {
  position: sticky;
  top: 88px;
  min-height: 72vh;
  border: 1px solid var(--line-soft);
  border-radius: 30px;
  background:
    radial-gradient(circle at 50% 50%, rgba(58, 92, 255, 0.12), transparent 42%),
    radial-gradient(circle at 16% 6%, rgba(255, 61, 61, 0.16), transparent 26%),
    linear-gradient(180deg, rgba(3, 4, 7, 0.74), rgba(2, 2, 4, 0.95));
  overflow: hidden;
}

.skills-web-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 50% 50%, rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.82) 72%);
  z-index: 1;
}

.web-core,
.web-lines,
.web-glow {
  position: absolute;
  left: 50%;
  top: 50%;
  transform-origin: 50% 50%;
}

.web-core {
  width: calc(120px + var(--web-progress, 0) * 420px);
  aspect-ratio: 1;
  border-radius: 50%;
  border: 2px solid rgba(243, 247, 255, calc(0.18 + var(--web-progress, 0) * 0.5));
  transform: translate(-50%, -50%) scale(calc(0.4 + var(--web-progress, 0) * 0.9));
  filter: blur(calc(14px - var(--web-progress, 0) * 11px));
  z-index: 2;
}

.web-lines {
  width: calc(200px + var(--web-progress, 0) * 960px);
  aspect-ratio: 1;
  opacity: calc(0.16 + var(--web-progress, 0) * 0.82);
  z-index: 2;
}

.web-lines--ring {
  background:
    repeating-radial-gradient(
      circle at center,
      rgba(238, 244, 255, 0.92) 0 1.7px,
      transparent 2px 58px
    );
  transform: translate(-50%, -50%) rotate(calc(6deg - var(--web-progress, 0) * 9deg));
  filter: blur(calc(5px - var(--web-progress, 0) * 4px));
  animation: webDriftRing 9s ease-in-out infinite;
}

.web-lines--radial {
  background:
    repeating-conic-gradient(
      from 10deg,
      rgba(246, 248, 255, 0.96) 0 1.4deg,
      transparent 1.4deg 24deg
    );
  transform: translate(-50%, -50%) rotate(calc(-12deg + var(--web-progress, 0) * 5deg));
  filter: blur(calc(3px - var(--web-progress, 0) * 2px));
  animation: webDriftRadial 11s ease-in-out infinite;
}

.web-glow {
  width: calc(180px + var(--web-progress, 0) * 360px);
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(134, 170, 255, 0.3), rgba(134, 170, 255, 0) 70%);
  transform: translate(-50%, -50%) scale(calc(0.5 + var(--web-progress, 0) * 0.95));
  opacity: calc(0.35 + var(--web-progress, 0) * 0.4);
  z-index: 1;
  animation: webPulse 5.4s ease-in-out infinite;
}

@keyframes webDriftRing {
  0%,
  100% {
    transform: translate(-50%, -50%) rotate(calc(6deg - var(--web-progress, 0) * 9deg)) scale(1);
  }
  50% {
    transform: translate(-50%, -50%) rotate(calc(10deg - var(--web-progress, 0) * 12deg)) scale(1.015);
  }
}

@keyframes webDriftRadial {
  0%,
  100% {
    transform: translate(-50%, -50%) rotate(calc(-12deg + var(--web-progress, 0) * 5deg)) scale(1);
  }
  50% {
    transform: translate(-50%, -50%) rotate(calc(-18deg + var(--web-progress, 0) * 7deg)) scale(0.99);
  }
}

@keyframes webPulse {
  0%,
  100% {
    opacity: calc(0.3 + var(--web-progress, 0) * 0.34);
    transform: translate(-50%, -50%) scale(calc(0.52 + var(--web-progress, 0) * 0.92));
  }
  50% {
    opacity: calc(0.48 + var(--web-progress, 0) * 0.46);
    transform: translate(-50%, -50%) scale(calc(0.58 + var(--web-progress, 0) * 0.99));
  }
}

.skill-card {
  padding: 24px;
  backdrop-filter: blur(2px);
  background: rgba(16, 20, 33, 0.52);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 22px;
  opacity: calc(0.2 + var(--card-progress, 0) * 0.8);
  transform: translateY(calc(28px - var(--card-progress, 0) * 28px)) scale(calc(0.95 + var(--card-progress, 0) * 0.05));
  filter: blur(calc(6px - var(--card-progress, 0) * 6px));
  transition: opacity 0.28s ease, transform 0.32s ease, filter 0.3s ease;
}

.skill-card h3 {
  color: #82d5ff;
  margin-bottom: 8px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  align-items: start;
  perspective: 1300px;
  transform-style: preserve-3d;
  overflow: visible;
}

.project-card {
  min-height: 360px;
  padding: 16px;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  transform-origin: 50% 88%;
  z-index: calc(10 + var(--fan-z, 1));
  transform:
    translateX(calc(var(--fan-x, 0px) * var(--projects-fan-progress, 0)))
    translateY(calc(var(--fan-y, 0px) * var(--projects-fan-progress, 0)))
    rotate(calc(var(--fan-rot, 0deg) * var(--projects-fan-progress, 0)))
    scale(calc(1 + var(--fan-scale, 0) * var(--projects-fan-progress, 0)));
  filter: blur(calc(var(--projects-fan-progress, 0) * 0.52px));
  opacity: calc(1 - var(--projects-fan-progress, 0) * var(--fan-dim, 0.12));
  transition: transform 0.14s linear, filter 0.14s linear, opacity 0.14s linear;
}

.project-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.38));
  opacity: calc(var(--projects-fan-progress, 0) * 0.38);
  pointer-events: none;
}

.project-cover {
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 24px;
  min-height: 214px;
  position: relative;
  display: grid;
  place-items: center;
  margin-bottom: 10px;
  background:
    radial-gradient(circle at 50% 40%, rgba(255, 255, 255, 0.14), transparent 56%),
    radial-gradient(circle at 20% 20%, rgba(108, 124, 255, 0.2), transparent 48%),
    repeating-radial-gradient(circle at center, rgba(255, 255, 255, 0.03) 0 2px, transparent 2px 6px);
  transition: transform 0.28s var(--ease-hero), filter 0.28s var(--ease-hero);
}

.project-logo {
  font-size: 3rem;
  font-weight: 800;
}

.project-impact {
  position: absolute;
  right: 12px;
  top: 10px;
  font-family: 'Bangers', 'Archivo Black', sans-serif;
  font-size: 1.3rem;
  opacity: 0;
  transform: translateY(-8px) rotate(-8deg);
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.project-burst,
.project-rays {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.22s ease;
}

.project-burst {
  background: radial-gradient(circle at 70% 20%, rgba(255, 255, 255, 0.34), transparent 36%);
}

.project-rays {
  background: conic-gradient(from 220deg at 50% 50%, rgba(255, 255, 255, 0.88), transparent 12%, rgba(255, 255, 255, 0.3), transparent 32%);
  mix-blend-mode: screen;
}

.project-card:hover .project-cover {
  transform: translateY(-8px) scale(1.03);
}

.project-card:hover .project-impact,
.project-card:hover .project-burst,
.project-card:hover .project-rays {
  opacity: 1;
}

.project-card--1:hover .project-cover {
  background:
    linear-gradient(130deg, rgba(58, 255, 188, 0.34), rgba(16, 17, 24, 0.48)),
    repeating-conic-gradient(from 0deg, rgba(255, 255, 255, 0.24) 0 8deg, transparent 8deg 20deg);
}

.project-card--2:hover .project-cover {
  background:
    linear-gradient(140deg, rgba(255, 76, 76, 0.34), rgba(42, 112, 255, 0.3)),
    url('https://www.eiddie.me/projects/futmap/futmap-cover-art.jpg') center/cover no-repeat;
}

.project-card--3:hover .project-cover {
  background:
    linear-gradient(140deg, rgba(123, 108, 255, 0.35), rgba(18, 22, 42, 0.6)),
    repeating-linear-gradient(160deg, rgba(255, 255, 255, 0.1) 0 2px, transparent 2px 10px);
}

.project-card--4:hover .project-cover {
  background:
    linear-gradient(120deg, rgba(42, 190, 255, 0.38), rgba(255, 133, 88, 0.34)),
    radial-gradient(circle at 70% 30%, rgba(255, 255, 255, 0.34), transparent 40%);
}

.project-index {
  font-size: 12px;
  color: var(--ink-muted);
  margin-bottom: 2px;
}

.project-card h3 {
  margin-top: 2px;
  font-size: 1.8rem;
  line-height: 1.15;
}

.project-type {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-muted);
}

.project-body {
  margin-top: 10px;
  color: var(--ink-soft);
  line-height: 1.65;
}

.contact-panel {
  min-height: 74vh;
  max-width: 900px;
  width: min(900px, 100%);
  margin: 0 auto;
  padding: 42px;
  display: grid;
  align-content: center;
  position: relative;
  overflow: hidden;
  transform: translateY(calc((1 - var(--contact-progress, 0)) * 26px)) scale(calc(0.965 + var(--contact-progress, 0) * 0.035));
  transition: transform 0.2s linear;
}

.contact-panel::before {
  content: '';
  position: absolute;
  inset: -12% -8%;
  background:
    radial-gradient(circle at 75% 18%, rgba(63, 120, 255, 0.22), transparent 42%),
    radial-gradient(circle at 12% 84%, rgba(255, 65, 86, 0.16), transparent 38%);
  opacity: calc(0.24 + var(--contact-progress, 0) * 0.5);
  transform: translateY(calc((1 - var(--contact-progress, 0)) * 14px));
  transition: opacity 0.2s linear, transform 0.2s linear;
  pointer-events: none;
}

.contact-panel > * {
  position: relative;
  z-index: 1;
}

.contact-panel h2 {
  margin: 12px 0 18px;
  max-width: 680px;
  line-height: 0.94;
  font-size: clamp(2.3rem, 5.1vw, 5.4rem);
}

.contact-title {
  display: grid;
  gap: 2px;
}

.contact-title-line {
  display: block;
  width: fit-content;
  transform: translateX(0);
  opacity: 0;
  filter: blur(1.2px);
  transition: transform 0.2s linear, opacity 0.2s linear, filter 0.2s linear;
}

.contact-title-line--left {
  transform: translateX(calc((1 - var(--line-progress, 0)) * -78px));
  opacity: calc(0.22 + var(--line-progress, 0) * 0.78);
  filter: blur(calc((1 - var(--line-progress, 0)) * 2px));
}

.contact-title-line--right {
  justify-self: end;
  transform: translateX(calc((1 - var(--line-progress, 0)) * 78px));
  opacity: calc(0.22 + var(--line-progress, 0) * 0.78);
  filter: blur(calc((1 - var(--line-progress, 0)) * 2px));
}

.contact-panel p {
  max-width: 640px;
  color: var(--ink-soft);
  margin-bottom: 20px;
  transform: translateY(calc((1 - var(--contact-copy-progress, 0)) * 14px));
  opacity: calc(0.14 + var(--contact-copy-progress, 0) * 0.86);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.contact-cta-btn {
  width: min(780px, 100%);
  justify-self: center;
  height: 40px;
  border-radius: 999px;
  font-weight: 700;
  letter-spacing: 0.02em;
  transform: translateY(calc((1 - var(--contact-cta-progress, 0)) * 20px)) scale(calc(0.96 + var(--contact-cta-progress, 0) * 0.04));
  opacity: calc(0.2 + var(--contact-cta-progress, 0) * 0.8);
  transition: transform 0.2s linear, opacity 0.2s linear;
}

.contact-section {
  display: flex;
  justify-content: center;
}

@media (max-width: 1100px) {
  .project-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .project-card {
    transform:
      translateX(calc(var(--fan-x, 0px) * var(--projects-fan-progress, 0) * 0.42))
      translateY(calc(var(--fan-y, 0px) * var(--projects-fan-progress, 0) * 0.68))
      rotate(calc(var(--fan-rot, 0deg) * var(--projects-fan-progress, 0) * 0.52))
      scale(calc(1 + var(--fan-scale, 0) * var(--projects-fan-progress, 0)));
  }
}

@media (max-width: 900px) {
  .hero-stage {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .name-poster,
  .about-layout {
    grid-template-columns: 1fr;
  }

  .about-metrics,
  .about-details {
    grid-template-columns: 1fr;
  }

  .skills-stage {
    min-height: 158vh;
  }

  .skills-web-shell {
    top: 78px;
    min-height: 64vh;
  }

  .name-ghost {
    grid-auto-flow: column;
    overflow: auto;
  }
}

@media (max-width: 760px) {
  .topbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .hero-stage,
  .name-section,
  .contact-panel {
    padding: 24px;
  }

  .skills-stage {
    padding: 16px;
  }

  .skills-grid {
    padding: 16px;
  }

  .project-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .project-card {
    transform: none;
    filter: none;
    opacity: 1;
  }

  .hero-actions .btn-pill {
    width: 100%;
  }
}
</style>
