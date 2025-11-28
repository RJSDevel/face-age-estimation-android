package pro.yagupov.faceageestimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import pro.yagupov.faceageestimate.ui.theme.FaceAgeEstimateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceAgeEstimateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    AgeGenderCameraScreen(viewModel())
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AgeGenderCameraScreen(viewModel: AgeViewModel) {
    val cameraPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.CAMERA
    )

    LaunchedEffect(cameraPermissionState) {
        // Запрашиваем разрешение при первом входе
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        viewModel.initialize(LocalContext.current)
        CameraPreviewContent(viewModel = viewModel)
    }

}

@Composable
fun CameraPreviewContent(viewModel: AgeViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val prediction by viewModel.prediction.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                viewModel.startCamera(lifecycleOwner, previewView.surfaceProvider)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        prediction?.let { pred ->
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Age: ${pred.age}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        viewModel.faceFound.let { it ->
            if (!it.value) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Face not found", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}