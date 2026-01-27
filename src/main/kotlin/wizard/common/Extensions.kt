package wizard.common

import com.android.tools.idea.wizard.template.Thumb
import java.net.URI

fun getImage(className: String, imagePath: String): Thumb {
    val pluginClassLoader =
        Class.forName("wizard.projectwizard.${className}Kt").classLoader
    val imageUrl = pluginClassLoader?.getResource("images/$imagePath.png")
    return if (imageUrl != null) {
        Thumb { imageUrl }
    } else {
        Thumb { URI("https://canerture.com/$imagePath.png").toURL() }
    }
}
