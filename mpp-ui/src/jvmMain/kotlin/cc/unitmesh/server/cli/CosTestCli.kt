package cc.unitmesh.server.cli

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

/**
 * Test COS bucket region
 */
object CosTestCli {
    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_RED = "\u001B[31m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_BLUE = "\u001B[34m"

    @JvmStatic
    fun main(args: Array<String>) {
        val bucket = args.getOrNull(0) ?: "autodev-1251908290"
        
        val regions = listOf(
            "ap-guangzhou",   // 广州
            "ap-shanghai",    // 上海
            "ap-beijing",     // 北京
            "ap-nanjing",     // 南京
            "ap-chengdu",     // 成都
            "ap-chongqing",   // 重庆
            "ap-shenzhen-fsi",// 深圳金融
            "ap-shanghai-fsi",// 上海金融
            "ap-beijing-fsi", // 北京金融
            "ap-hongkong",    // 香港
            "ap-singapore",   // 新加坡
            "ap-mumbai",      // 孟买
            "ap-seoul",       // 首尔
            "ap-bangkok",     // 曼谷
            "ap-tokyo",       // 东京
            "na-siliconvalley",// 硅谷
            "na-ashburn",     // 弗吉尼亚
            "na-toronto",     // 多伦多
            "sa-saopaulo",    // 圣保罗
            "eu-frankfurt",   // 法兰克福
            "eu-moscow"       // 莫斯科
        )

        println("${ANSI_BLUE}Testing bucket: $bucket$ANSI_RESET")
        println()

        runBlocking {
            val client = HttpClient(CIO) {
                expectSuccess = false
            }

            for (region in regions) {
                val host = "$bucket.cos.$region.myqcloud.com"
                val url = "https://$host/"
                
                try {
                    val response = client.head(url)
                    val status = response.status.value
                    
                    when {
                        status in 200..299 -> {
                            println("${ANSI_GREEN}✓ Found! Region: $region (HTTP $status)$ANSI_RESET")
                            println("  ${ANSI_BLUE}Host: $host$ANSI_RESET")
                            break
                        }
                        status == 403 -> {
                            println("${ANSI_YELLOW}⚠ Region: $region - Bucket exists but access denied (HTTP $status)$ANSI_RESET")
                            println("  ${ANSI_BLUE}Host: $host$ANSI_RESET")
                            break
                        }
                        status == 404 -> {
                            print("${ANSI_RED}✗$ANSI_RESET ")
                        }
                        else -> {
                            println("${ANSI_YELLOW}? Region: $region - HTTP $status$ANSI_RESET")
                        }
                    }
                } catch (e: Exception) {
                    print("${ANSI_RED}✗$ANSI_RESET ")
                }
            }

            client.close()
        }
    }
}

