import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync, existsSync } from 'node:fs'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 加载环境变量（.env / .env.development / .env.production）
  const env = loadEnv(mode, process.cwd(), '')

  // SSL 证书路径：优先从环境变量读取，默认使用同级 ssl 目录
  // 生成方式: localhost 127.0.0.1 ::1
  const certPath = env.SSL_CERT_PATH || fileURLToPath(new URL('./ssl/localhost+2.pem', import.meta.url))
  const keyPath = env.SSL_KEY_PATH || fileURLToPath(new URL('./ssl/localhost+2-key.pem', import.meta.url))

  // 开发服务器是否启用 HTTPS（证书存在时自动启用）
  const enableHttps = env.SSL_ENABLED !== 'false' && existsSync(certPath) && existsSync(keyPath)

  // 后端代理目标地址
  const apiTarget = env.VITE_PROXY_TARGET || 'https://localhost:8080'

  return {
    plugins: [
      vue(),
      // Element Plus 按需自动导入
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ['vue', 'vue-router', 'pinia'],
        dts: 'src/auto-imports.d.ts'
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts'
      })
    ],
    // sockjs-client 依赖 Node.js 的 global 变量，浏览器中不存在，需手动 polyfill
    define: {
      global: 'globalThis'
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: env.VITE_DEV_HOST || '0.0.0.0',
      port: 5173,
      ...(enableHttps ? {
        https: {
          cert: readFileSync(certPath),
          key: readFileSync(keyPath)
        }
      } : {}),
      proxy: {
        // REST API 代理
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          secure: false
        },
        //MinIO
        '/minio': { 
          target: apiTarget, 
          changeOrigin: true, 
          secure: false 
        }, 
        // WebSocket 代理
        '/ws-chat': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
          ws: true
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          // 自动注入全局变量到每个 scss 文件
          additionalData: `@use "@/styles/variables.scss" as *;`
        }
      }
    }
  }
})
