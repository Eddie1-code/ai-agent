import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Chat from '../views/ChatPage.vue'
import SuperAgent from '../views/SuperAgent.vue'
import LoveMaster from '../views/LoveMaster.vue'
import Receipt3D from '../views/Receipt3D.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/chat',
    name: 'Chat',
    component: Chat
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: SuperAgent
  },
  {
    path: '/love-master',
    name: 'LoveMaster',
    component: LoveMaster
  },
  {
    path: '/receipt-3d',
    name: 'Receipt3DMain',
    component: Receipt3D
  },
  {
    path: '/legacy-receipt-3d',
    name: 'Receipt3D',
    component: Receipt3D
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router