import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Chat from '../views/ChatPage.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Profile from '../views/Profile.vue'
import Receipt3D from '../views/Receipt3D.vue'
import { getAuthToken } from '../api'

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
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile
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

const protectedRoutes = new Set(['/chat', '/profile'])

router.beforeEach((to, from, next) => {
  if (!protectedRoutes.has(to.path)) {
    next()
    return
  }
  const token = getAuthToken()
  if (!token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router