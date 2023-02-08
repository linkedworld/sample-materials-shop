/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.curiouscreature.compose.sample.shop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.sharp.Add
import androidx.compose.material.icons.sharp.Remove
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider

import com.google.android.filament.*
import com.google.android.filament.Colors
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.utils.KtxLoader
import com.google.android.filament.utils.Utils

import com.curiouscreature.compose.R


class MainActivity : AppCompatActivity() {
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var engine: Engine
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader

    private lateinit var indirectLight: IndirectLight
    private lateinit var skybox: Skybox
    private var light: Int = 0

    companion object {
        init {
            Utils.init()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initFilament()

        storeViewModel = ViewModelProvider(this)[StoreViewModel::class.java]
        val shoppingCart = storeViewModel.shoppingCart

        val increase: (Product) -> Unit = { product ->
            storeViewModel.update(product.copy(quantity = product.quantity + 1))
        }

        val decrease: (Product) -> Unit = { product ->
            if (product.quantity > 1) {
                storeViewModel.update(product.copy(quantity = product.quantity - 1))
            } else {
                storeViewModel.delete(product)
            }
        }

        val updateColor: (Product) -> Unit = { product ->
            storeViewModel.update(product.copy(color = nextProductColor(product.color)))
        }

        setContent {
            StoreTheme {
                Scaffold(
                    topBar = { StoreAppBar() },
                    floatingActionButton = { StoreCheckout(shoppingCart) }
                ) { padding ->
                    ShoppingCart(shoppingCart, increase, decrease, updateColor, padding)
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()

        engine.lightManager.destroy(light)
        engine.destroyEntity(light)
        engine.destroyIndirectLight(indirectLight)
        engine.destroySkybox(skybox)

        scenes.forEach {
            engine.destroyScene(it.value.scene)
            assetLoader.destroyAsset(it.value.asset)
        }

        assetLoader.destroy()
        resourceLoader.destroy()

        engine.destroy()
    }
    private fun initFilament() {
        engine = Engine.create()
        assetLoader = AssetLoader(engine, MaterialProvider(engine), EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        val ibl = "courtyard_8k"
        readCompressedAsset(this, "envs/${ibl}/${ibl}_ibl.ktx").let {
            indirectLight = KtxLoader.createIndirectLight(engine, it)
            indirectLight.intensity = 30_000.0f
        }

        readCompressedAsset(this, "envs/${ibl}/${ibl}_skybox.ktx").let {
            skybox = KtxLoader.createSkybox(engine, it)
        }

        light = EntityManager.get().create()
        val (r, g, b) = Colors.cct(6_000.0f)
        LightManager.Builder(LightManager.Type.SUN)
            .color(r, g, b)
            .intensity(70_000.0f)
            .direction(0.28f, -0.6f, -0.76f)
            .build(engine, light)

        fun createScene(name: String, gltf: String) {
            val scene = engine.createScene()
            val asset = readCompressedAsset(this, gltf).let {
                val asset = loadModelGlb(assetLoader, resourceLoader, it)
                transformToUnitCube(engine, asset)
                asset
            }
            scene.indirectLight = indirectLight
            scene.skybox = skybox

            scene.addEntities(asset.entities)

            scene.addEntity(light)

            scenes[name] = ProductScene(engine, scene, asset)
        }

        createScene("Car paint", "models/car_paint/material_car_paint.glb")
        createScene("Carbon fiber", "models/carbon_fiber/material_carbon_fiber.glb")
        createScene("Lacquered wood", "models/lacquered_wood/material_lacquered_wood.glb")
        createScene("Wood", "models/wood/material_wood.glb")
    }
}

@Composable
fun ShoppingCart( shoppingCart: LiveData<List<Product>>, increase: (Product) -> Unit, decrease: (Product) -> Unit, updateColor: (Product) -> Unit, padding: PaddingValues) {
    val products by shoppingCart.observeAsState(emptyList())

    LazyColumn(modifier = Modifier.padding(padding)) {
        itemsIndexed(products) { index, item ->
            ShoppingCartItem(item, increase, decrease, updateColor) {
                FilamentViewer(item)
            }
        }
    }
}

@Composable
fun ShoppingCartItem(product: Product, increase: (Product) -> Unit = {}, decrease: (Product) -> Unit = {}, updateColor: (Product) -> Unit = {}, content: @Composable () -> Unit = {}) {
    val (selected, onSelected) = remember { mutableStateOf(false) }

    var topLeftCornerRadius by remember { mutableStateOf(8f) }
    var cornerRadius by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f, targetValue = if (selected) 48f else 8f,
            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        ) { value, _ ->
            topLeftCornerRadius = value
        }
        animate(
            initialValue = 0f, targetValue = if (selected) 0f else 8f,
            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        ) { value, _ ->
            cornerRadius = value
        }
    }

    Surface(
        modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 8.dp),
            shape = RoundedCornerShape(
                topStart = topLeftCornerRadius.dp,
                topEnd = cornerRadius.dp,
                bottomStart = cornerRadius.dp,
                bottomEnd = cornerRadius.dp
            ),
        elevation = 4.dp
    ) {
        Column {
            Box( modifier = Modifier.toggleable(value = selected, onValueChange = onSelected)) {

                var selectedAlpha by remember { mutableStateOf(0f) }

                content()

                LaunchedEffect(Unit) {
                    animate(initialValue = 0f, targetValue = if (selected) 0.65f else 0f, animationSpec = infiniteRepeatable(tween(durationMillis = 200, easing = LinearEasing))) { value, _ -> selectedAlpha = value }
                }

                Surface(
                    modifier = Modifier.matchParentSize(), color = MaterialTheme.colors.primary.copy(alpha = selectedAlpha)) {
                    Icon(painter = rememberVectorPainter(Icons.Filled.Done), contentDescription = "Done", tint = LocalContentColor.current.copy(alpha = selectedAlpha))
                }
            }

            ShoppingCartItemRow(product, decrease, increase, updateColor)
        }
    }
}

@Composable
fun ShoppingCartItemRow(product: Product, decrease: (Product) -> Unit = { }, increase: (Product) -> Unit = { }, updateColor: (Product) -> Unit = { }) {
    val hasColorSwatch = product.color.isProductColor
        ConstraintLayout(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            val (decreaseRef, increaseRef, labelRef, colorRef, amountRef) = createRefs()

            SmallButton(
                modifier = Modifier.constrainAs(decreaseRef) {
                    start.linkTo(parent.start)
                    centerVerticallyTo(parent)
                },
                onClick = { decrease(product) }
            ) {
                Image(Icons.Sharp.Remove, "")
            }

            SmallButton(
                modifier = Modifier.constrainAs(increaseRef) {
                    start.linkTo(decreaseRef.end, margin = 4.dp)
                    centerVerticallyTo(parent)
                },
                onClick = { increase(product) }
            ) {
                Image(Icons.Sharp.Add, "")
            }

            Text(
                modifier = Modifier.constrainAs(labelRef) {
                    start.linkTo(increaseRef.end, margin = 8.dp)
                    centerVerticallyTo(parent)
                },
                text = "${product.quantity}× ${product.material}"
            )

            if (hasColorSwatch) {
                SmallButton(
                    modifier = Modifier.constrainAs(colorRef) {
                        start.linkTo(labelRef.end, margin = 4.dp)
                        centerVerticallyTo(parent)
                    },
                    onClick = { updateColor(product) },
                    color = Color.White
                ) {
                    Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(productColor(product)))
                }
            }

            Text(
                modifier = Modifier.constrainAs(amountRef) {
                    linkTo(
                        start = (if (hasColorSwatch) colorRef else labelRef).end,
                        end = parent.end,
                        bias = 1.0f
                    )
                    centerVerticallyTo(parent)
                },
                text = formatAmount(product)
            )
        }
}

@Composable
fun FilamentViewer(product: Product) {
    var modelViewer by remember { mutableStateOf<ModelViewer?>(null) }

    LaunchedEffect (product) {
        withFrameNanos { frameTimeNanos ->
            modelViewer?.render(frameTimeNanos)
        }
    }

    SideEffect {
        val (engine, scene, asset) = scenes[product.material]!!
        modelViewer?.scene = scene

        asset.entities.find { asset.getName(it)?.startsWith("car_paint_red") ?: false }?.also { entity ->
            val manager = engine.renderableManager
            val instance = manager.getInstance(entity)
            val material = manager.getMaterialInstanceAt(instance, 0)

            val productColor = productColor(product)

            val r = productColor.red
            val g = productColor.green
            val b = productColor.blue

            material.setParameter (
                "baseColorFactor", Colors.RgbaType.SRGB, r, g, b, 1.0f
            )
        }
    }

    AndroidView({ context ->
        LayoutInflater.from(context).inflate(
            R.layout.filament_host, FrameLayout(context), false
        ).apply {
            val (engine) = scenes[product.material]!!
            modelViewer = ModelViewer(engine, this as SurfaceView).also {
                setupModelViewer(it)
            }
        }
    })
}

@Composable
fun SmallButton(modifier: Modifier = Modifier, onClick: () -> Unit = { }, color: Color = MaterialTheme.colors.secondary, content: @Composable () -> Unit = { }) {
    Surface(
        modifier = modifier.size(16.dp),
        //contentAlignment = Alignment.CenterVertically, //TODO
        color = color,
        shape = CircleShape,
        elevation = 2.dp
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun StoreAppBar() {
    TopAppBar(
        title = { Text(LocalContext.current.getString(R.string.app_name), color=Color.White)},
        backgroundColor = Color(0x21, 0x96, 0xF3, 0xFF)
    )
}

@Composable
fun StoreCheckout(shoppingCart: LiveData<List<Product>>) {
    val products = shoppingCart.observeAsState(emptyList()).value
    ExtendedFloatingActionButton(
        text = { Text("${products.sumOf { it.quantity }} items") },
        icon = {
            Icon(Icons.Filled.ShoppingCart, "Shopping Cart", modifier = Modifier)
        },
        onClick = { }
    )
}
