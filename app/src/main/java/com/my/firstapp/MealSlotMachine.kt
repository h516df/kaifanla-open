package com.my.firstapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.floor

/** All three drums describe one real candidate, never three independently random combinations. */
data class MealReelItem(val label:String,val detail:String,val emoji:String,
    val priceLabel:String="待确认",val shopLabel:String=detail)
fun reelIndex(position:Int,count:Int):Int=if(count<=0)-1 else ((position%count)+count)%count
fun reelTarget(start:Float,count:Int,selected:Int):Float {
    require(count>0 && selected in 0 until count)
    val whole=kotlin.math.ceil(start.toDouble()).toInt()
    return (whole+count*5+reelIndex(selected-reelIndex(whole,count),count)).toFloat()
}

/** A single physical reel carries a complete meal. Store, dish and price cannot diverge. */
@Composable
fun MealSlotMachine(items:List<MealReelItem>,position:(Int)->Float,spinning:Boolean,onGo:()->Unit) {
    if(items.isEmpty())return
    val compact=LocalConfiguration.current.screenHeightDp<740
    val metal=AppColors.Primary.copy(alpha=.65f)
    Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).shadow(4.dp,RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp)).background(AppColors.Surface)
            .border(1.dp,AppColors.Line,RoundedCornerShape(32.dp)).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("开饭好运机",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=AppColors.Text)
            Spacer(Modifier.height(12.dp))
            val row=if(compact)58.dp else 76.dp
            Box(Modifier.fillMaxWidth().height(row*3).clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(AppColors.Line,AppColors.Light,AppColors.Light,AppColors.Line)))
                .border(2.dp,AppColors.Line,RoundedCornerShape(20.dp))
                .semantics {
                    contentDescription="完整餐食卷轴"
                    val item=items[reelIndex(floor(position(0).toDouble()).toInt(),items.size)]
                    stateDescription=if(spinning)"正在转动" else "${item.shopLabel}，${item.label}，${item.priceLabel}"
                }) {
                val v=position(0);val whole=floor(v.toDouble()).toInt();val fraction=v-whole
                for(relative in -2..2) {
                    val item=items[reelIndex(whole+relative,items.size)]
                    val center=relative==0 && fraction<.05f
                    Row(Modifier.fillMaxWidth().offset(y=row*(relative+1-fraction)).height(row)
                        .padding(horizontal=12.dp).clearAndSetSemantics {},verticalAlignment=Alignment.CenterVertically) {
                        Text(item.emoji,fontSize=27.sp,modifier=Modifier.padding(end=10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label,fontSize=18.sp,fontWeight=FontWeight.SemiBold,
                                color=AppColors.Text.copy(alpha=if(center)1f else .35f),maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text("${item.shopLabel} · ${item.priceLabel}",fontSize=12.sp,
                                color=AppColors.SubText.copy(alpha=if(center)1f else .35f),maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(row).align(Alignment.Center).border(1.dp,metal,RoundedCornerShape(12.dp)))
                Box(Modifier.fillMaxWidth().height(45.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(AppColors.Surface.copy(alpha=.8f),Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(45.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent,AppColors.Surface.copy(alpha=.8f)))))
            }
            Row(Modifier.padding(top=16.dp,bottom=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                repeat(5) { index -> Box(Modifier.size(4.dp).clip(CircleShape).background(
                    if(spinning && (floor(position(0).toDouble()).toInt()+index)%2==0)AppColors.Line else metal)) }
            }
            if(!compact)Text(if(spinning)"好运正在路上" else "轻拉右侧拉杆，也可点下方按钮",fontSize=12.sp,color=AppColors.SubText,textAlign=TextAlign.Center)
            Box(Modifier.padding(top=16.dp).fillMaxWidth(.55f).height(7.dp).clip(CircleShape).background(AppColors.Text.copy(alpha=.7f)))
        }
        MachineLever(spinning,onGo,metal,Modifier.width(48.dp).height(190.dp))
    }
}

@Composable
private fun MachineLever(spinning:Boolean,onGo:()->Unit,metal:Color,modifier:Modifier) {
    var pull by remember { mutableFloatStateOf(0f) }
    val latestGo by rememberUpdatedState(onGo)
    val latestSpinning by rememberUpdatedState(spinning)
    Box(modifier.semantics { contentDescription="拉杆，选择这一餐";role=Role.Button }
        .clickable(enabled=!spinning){latestGo()}
        .pointerInput(spinning) {
            if(!spinning)detectVerticalDragGestures(
                onDragStart={pull=0f},
                onVerticalDrag={change,amount->change.consume();pull=(pull+amount).coerceIn(0f,64.dp.toPx())},
                onDragEnd={val trigger=pull>=28.dp.toPx();pull=0f;if(trigger && !latestSpinning)latestGo()},
                onDragCancel={pull=0f},
            )
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val x=size.width*.48f;val pivot=size.height*.75f;val top=size.height*.15f+pull
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF756056),metal,Color(0xFF917A69))),Offset(0f,pivot-18.dp.toPx()),Size(22.dp.toPx(),36.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            drawLine(Color(0xFF69554F),Offset(x,pivot),Offset(x,top),8.dp.toPx())
            drawLine(metal,Offset(x-1.dp.toPx(),pivot),Offset(x-1.dp.toPx(),top),3.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(AppColors.Primary,AppColors.Deep),Offset(x-4.dp.toPx(),top-5.dp.toPx()),18.dp.toPx()),15.dp.toPx(),Offset(x,top))
            drawCircle(Color.White.copy(alpha=.35f),4.dp.toPx(),Offset(x-4.dp.toPx(),top-5.dp.toPx()))
        }
    }
}
