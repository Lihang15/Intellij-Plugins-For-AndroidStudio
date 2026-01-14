package wizard

import com.android.tools.idea.wizard.template.Thumb
import java.net.URI

/**
 * 加载插件资源中的图片
 * 参考 cnrture/QuickProjectWizard 的实现方式
 */
fun getImage(className: String, imagePath: String): Thumb {
    val pluginClassLoader = try {
        Class.forName("wizard.${className}Kt").classLoader
    } catch (e: Exception) {
        // 如果找不到类，使用当前类加载器
        ImageUtils::class.java.classLoader
    }
    
    val imageUrl = pluginClassLoader?.getResource("icons/$imagePath.png")
    
    return if (imageUrl != null) {
        Thumb { imageUrl }
    } else {
        // 回退：使用网络图片或默认图片
        Thumb { URI("https://via.placeholder.com/256x256.png?text=OHOS+KMP").toURL() }
    }
}

// 用于类引用
private object ImageUtils
