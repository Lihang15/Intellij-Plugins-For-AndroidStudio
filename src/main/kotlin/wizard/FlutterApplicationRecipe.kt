package wizard


import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor


fun RecipeExecutor.flutterApplicationRecipe(
    moduleData: ModuleTemplateData,
    projectName: String,
    sdkPath: String,
    org: String,
    useKotlin: Boolean,
    useSwift: Boolean,

) {
    val projectRoot = moduleData.rootDir

    // 构建flutter create命令参数
//    val settings = FlutterCreateAdditionalSettings.Builder()
//        .setType(FlutterProjectType.APP)
//        .setOrg(org)
//        .setKotlin(useKotlin)
//        .setPlatformAndroid(platforms.contains("Android"))
//        .setPlatformIos(platforms.contains("iOS"))
//        .setPlatformWeb(platforms.contains("Web"))
//        .setPlatformWindows(platforms.contains("Windows"))
//        .setPlatformMacos(platforms.contains("macOS"))
//        .setPlatformLinux(platforms.contains("Linux"))
//        .build()

//    settings.projectName = projectName

    // 执行flutter create
//    val sdk = FlutterSdk.forPath(sdkPath)
//    if (sdk != null) {
//        val command = sdk.flutterCreate(projectRoot, settings)
        // 在后台执行命令
        executeFlutterCreate()
    }

private fun RecipeExecutor.executeFlutterCreate(

) {
//    // 使用IntelliJ的进度管理器执行命令
//    ProgressManager.getInstance().run(object : Task.Backgroundable(
//        project,
//        "Creating Flutter Project...",
//        true
//    ) {
//        override fun run(indicator: ProgressIndicator) {
//            indicator.text = "Running flutter create..."
//            command.start(null, null)
//        }
//    })
    println("zhixing")
}