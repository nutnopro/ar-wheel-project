package com.arwheelapp.modules

import android.util.Log // 🌟 เพิ่ม Import
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import java.lang.Float.max
import kotlinx.coroutines.*

class ModelManager(private val arSceneView: ARSceneView) {
    private val TAG = "ModelManager" // 🌟 ตั้งชื่อ Tag สำหรับ Filter ใน Logcat
    private val modelLoader by lazy { ModelLoader(arSceneView.engine, arSceneView.context) }
    private val materialLoader by lazy { MaterialLoader(arSceneView.engine, arSceneView.context) }
    private var cachedOcclusionMaterial: MaterialInstance? = null

    fun createNewModel(modelPath: String, scope: CoroutineScope): Node {
        Log.d(TAG, "createNewModel: Loading model from $modelPath")
        val rootNode = Node(arSceneView.engine).apply { isVisible = false }

        scope.launch {
            loadOcclusionMaterial()
            val modelInstance = modelLoader.createModelInstance(modelPath)

            withContext(Dispatchers.Main) {
                modelInstance?.let {
                    Log.d(TAG, "createNewModel: Model loaded successfully")
                    setupWheelSystem(rootNode, ModelNode(modelInstance = it))
                    rootNode.isVisible = true
                } ?: Log.e(TAG, "createNewModel: Failed to load model instance")
            }
        }

        return rootNode
    }

    private suspend fun loadOcclusionMaterial() {
        if (cachedOcclusionMaterial == null) {
            cachedOcclusionMaterial = withContext(Dispatchers.IO) {
                val material = materialLoader.loadMaterial("materials/occlusion.filamat")
                if (material == null) {
                    Log.e(TAG, "loadOcclusionMaterial: Could not find occlusion.filamat in assets!")
                } else {
                    Log.d(TAG, "loadOcclusionMaterial: Material file loaded")
                }
                material?.createInstance()
            }
        }
    }

    private fun setupWheelSystem(rootNode: Node, wheelNode: ModelNode) {
        val box = wheelNode.boundingBox
        val diameter = max(box.halfExtent[0] * 2f, box.halfExtent[2] * 2f)
        val thickness = box.halfExtent[1] * 2f
        val halfThickness = thickness / 2f

        Log.d(TAG, "setupWheelSystem: Calculated Diameter=$diameter, Thickness=$thickness")

        wheelNode.position = Float3(0f, -halfThickness, 0f)

        // สร้าง Tube สำหรับเจาะรู (Occlusion)
        val tube = createComponent("models/tube.glb", diameter * 1.05f, thickness)?.apply {
            position = Float3(0f, -halfThickness, 0f)
            applyOcclusion()
        }
        tube?.let { rootNode.addChildNode(it) }

        // สร้าง Backplate ปิดหลังล้อ
        val backplate = createComponent("models/backplate.glb", diameter, 0.01f)?.apply {
            position = Float3(0f, -thickness, 0f)
        }
        backplate?.let { rootNode.addChildNode(it) }

        rootNode.addChildNode(wheelNode)
        // rootNode.addChildNode(tube)
        // rootNode.addChildNode(backplate)
        Log.d(TAG, "setupWheelSystem: System nodes attached to root")
    }

    private fun createComponent(assetPath: String, diameter: Float, thickness: Float): ModelNode {
        Log.d(TAG, "createComponent: Loading $assetPath")
        val instance = modelLoader.createModelInstance(assetPath)
        if (instance == null) {
            Log.e(TAG, "createComponent: Error loading $assetPath")
            // return null
        }
        
        return ModelNode(modelInstance = instance).apply {
            scale = Float3(diameter, thickness, diameter)
        }
    }

    private fun ModelNode.applyOcclusion() {
        val occlusion = cachedOcclusionMaterial
        if (occlusion == null) {
            Log.e(TAG, "applyOcclusion: Cannot apply, material is null")
            return
        }
        
        val rm = engine.renderableManager
        var appliedCount = 0

        modelInstance?.entities?.forEach { entity ->
            val renderable = rm.getInstance(entity)
            if (renderable != 0) {
                for (i in 0 until rm.getPrimitiveCount(renderable)) {
                    rm.setMaterialInstanceAt(renderable, i, occlusion)
                    appliedCount++
                }
            }
        }
        Log.d(TAG, "applyOcclusion: Applied occlusion to $appliedCount primitives")
    }

    fun changeModel(rootNode: Node, modelPath: String, scope: CoroutineScope) {
        Log.d(TAG, "changeModel: Switching to $modelPath")
        scope.launch {
            val modelInstance = modelLoader.createModelInstance(modelPath)
            withContext(Dispatchers.Main) {
                Log.d(TAG, "changeModel: Clearing old nodes")
                rootNode.childNodes.forEach { it.destroy() }
                rootNode.clearChildNodes()

                modelInstance?.let {
                    setupWheelSystem(rootNode, ModelNode(modelInstance = it))
                    Log.d(TAG, "changeModel: New model system ready")
                } ?: Log.e(TAG, "changeModel: Failed to load $modelPath")
            }
        }
    }

    fun changeModelSize(rootNode: Node, scaleFactor: Float) {
        Log.d(TAG, "changeModelSize: Scaling rootNode to $scaleFactor")
        rootNode.scale = Float3(scaleFactor, scaleFactor, scaleFactor)
    }
}