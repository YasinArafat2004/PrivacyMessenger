package com.yasin.privacymessenger

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule


object CallManager {

    private const val TAG = "CallManager"

    private const val SERVER_URL = "https://privacymessenger.onrender.com"

    // 🔴 METERED TURN CREDENTIALS
    private const val TURN_USERNAME = "34f590628d56369328deedc7"
    private const val TURN_PASSWORD = "bKksrbruXeyqFrL3"

    private const val CALL_TIMEOUT_MS = 45_000L

    private var socket: Socket? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var targetCallAddress: String? = null
    private var currentUsername: String = ""

    // Remote ICE candidates that arrived before setRemoteDescription completed.
    // libwebrtc silently drops addIceCandidate() calls in that window, so we
    // buffer them here and drain once the remote description is ready.
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private var callTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ==========================================
    // CONNECTION STATE
    // ==========================================

    private val _socketConnected = MutableStateFlow(false)
    val socketConnected: StateFlow<Boolean> = _socketConnected

    private val _registered = MutableStateFlow(false)
    val registered: StateFlow<Boolean> = _registered

    // ==========================================
    // CALL STATE
    // ==========================================

    private val _incomingCall = MutableStateFlow<String?>(null)
    val incomingCall: StateFlow<String?> = _incomingCall

    private val _incomingCallerName = MutableStateFlow<String?>(null)
    val incomingCallerName: StateFlow<String?> = _incomingCallerName

    private val _callState = MutableStateFlow("IDLE")
    val callState: StateFlow<String> = _callState

    // ==========================================
    // MESSAGE EVENTS
    // ==========================================

    private val _incomingMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<JSONObject> = _incomingMessages

    private val _messageDelivered = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val messageDelivered: SharedFlow<JSONObject> = _messageDelivered

    private val _messageUndelivered = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val messageUndelivered: SharedFlow<JSONObject> = _messageUndelivered

    // ==========================================
    // INITIALIZATION
    // ==========================================

    @Synchronized
    fun init(context: Context, username: String) {
        if (peerConnectionFactory != null) {
            if (currentUsername != username) {
                Log.w(TAG, "CallManager already initialized for another user")
            }
            return
        }

        currentUsername = username
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        initWebRTC(context.applicationContext)
        initSocket()
    }

    private fun initWebRTC(context: Context) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    // ==========================================
    // CREATE A FRESH PEER CONNECTION FOR EACH CALL
    // ==========================================

    private fun createPeerConnection() {
        closePeerConnection() // always start clean

        val factory = peerConnectionFactory ?: run {
            Log.e(TAG, "PeerConnectionFactory not initialised")
            return
        }

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
        }

        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)

        // 🔴 METERED TURN SERVERS
        val iceServers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80")
                .createIceServer()
        )

        if (TURN_USERNAME.isNotBlank() && TURN_PASSWORD.isNotBlank()) {
            val turnUrls = listOf(
                "turn:global.relay.metered.ca:80",
                "turn:global.relay.metered.ca:80?transport=tcp",
                "turn:global.relay.metered.ca:443",
                "turns:global.relay.metered.ca:443?transport=tcp"
            )
            turnUrls.forEach { url ->
                iceServers.add(
                    PeerConnection.IceServer.builder(url)
                        .setUsername(TURN_USERNAME)
                        .setPassword(TURN_PASSWORD)
                        .createIceServer()
                )
            }
        } else {
            Log.w(TAG, "No TURN server configured – calls across strict NAT may fail")
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                sendIceCandidate(candidate)
            }

            // Remote audio track arrives here in Unified Plan.
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    // Ensure the audio session is in voice-call mode the moment
                    // a remote track actually arrives.
                    audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                    Log.d(TAG, "Remote audio track enabled")
                } else {
                    Log.w(TAG, "onTrack received non-audio or null track: $track")
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "PeerConnection state: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        _callState.value = "CONNECTED"
                        cancelCallTimeout()
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        Log.e(TAG, "PeerConnection FAILED")
                        finishCallLocally()
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "PeerConnection DISCONNECTED")
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE state: $newState")
                if (newState == PeerConnection.IceConnectionState.FAILED) {
                    Log.e(TAG, "ICE connection failed")
                    finishCallLocally()
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        peerConnection?.addTrack(localAudioTrack)
    }

    private fun closePeerConnection() {
        // Drop any candidates that never made it into the peer connection.
        pendingRemoteCandidates.clear()

        peerConnection?.let {
            it.close()
            it.dispose()
            peerConnection = null
        }
        localAudioTrack?.let {
            it.setEnabled(false)
            it.dispose()
            localAudioTrack = null
        }
        audioSource?.let {
            it.dispose()
            audioSource = null
        }
        targetCallAddress = null
    }

    // ==========================================
    // REMOTE ICE CANDIDATE QUEUE
    // ==========================================

    private fun drainPendingCandidates() {
        val pc = peerConnection ?: return
        if (pendingRemoteCandidates.isEmpty()) return
        Log.d(TAG, "Draining ${pendingRemoteCandidates.size} queued remote ICE candidates")
        pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    // ==========================================
    // AUDIO FOCUS & ROUTING
    // ==========================================

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        am.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()

            am.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        am.mode = AudioManager.MODE_NORMAL
    }

    // ==========================================
    // SOCKET INITIALIZATION
    // ==========================================

    private fun initSocket() {
        try {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
            }

            socket = IO.socket(SERVER_URL, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected")
                _socketConnected.value = true
                _registered.value = false
                socket?.emit("register", currentUsername)
            }

            socket?.on("registration-success") {
                _registered.value = true
            }

            socket?.on("registration-error") { args ->
                val data = args.firstOrNull() as? JSONObject
                Log.e(TAG, "Registration failed: ${data?.optString("message")}")
                _registered.value = false
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                _socketConnected.value = false
                _registered.value = false
            }

            // ----- Messages -----
            socket?.on("receive-message") { args ->
                (args.firstOrNull() as? JSONObject)?.let { _incomingMessages.tryEmit(it) }
            }
            socket?.on("message-delivered") { args ->
                (args.firstOrNull() as? JSONObject)?.let { _messageDelivered.tryEmit(it) }
            }
            socket?.on("message-undelivered") { args ->
                (args.firstOrNull() as? JSONObject)?.let { _messageUndelivered.tryEmit(it) }
            }

            // ----- Call signalling -----
            socket?.on("incoming-call") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val fromSocketId = data.optString("fromSocketId").ifEmpty {
                    data.optString("from")
                }
                val callerUsername = data.optString("fromUsername").ifEmpty { "Unknown" }
                val offer = data.optJSONObject("offer") ?: return@on

                // Prepare a fresh peer connection for this incoming call.
                createPeerConnection()

                // Keep the mic muted until the user answers.
                localAudioTrack?.setEnabled(false)

                targetCallAddress = fromSocketId
                _incomingCall.value = fromSocketId
                _incomingCallerName.value = callerUsername
                _callState.value = "RINGING"

                val remoteDesc = SessionDescription(
                    SessionDescription.Type.OFFER,
                    offer.getString("sdp")
                )
                // Drain any candidates that arrived in the tiny window between
                // socket dispatch and setRemoteDescription completing.
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        drainPendingCandidates()
                    }
                }, remoteDesc)

                Log.d(TAG, "Incoming call from $callerUsername ($fromSocketId)")
            }

            socket?.on("call-answered") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val answer = data.optJSONObject("answer") ?: return@on
                val remoteSocketId = data.optString("from")
                if (remoteSocketId.isNotEmpty()) targetCallAddress = remoteSocketId

                val remoteDesc = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    answer.getString("sdp")
                )
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        drainPendingCandidates()
                    }
                }, remoteDesc)

                // "CONNECTING", not "CONNECTED" – audio may take 1-3 s.
                _callState.value = "CONNECTING"
                // Do NOT cancel the timeout yet – ICE still needs to complete.
            }

            socket?.on("ice-candidate") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val candidateJson = data.optJSONObject("candidate") ?: return@on
                val candidate = IceCandidate(
                    candidateJson.optString("sdpMid"),
                    candidateJson.optInt("sdpMLineIndex"),
                    candidateJson.optString("candidate")
                )
                val pc = peerConnection
                if (pc?.remoteDescription == null) {
                    // Remote description not set yet – queue. libwebrtc would
                    // silently discard this otherwise.
                    pendingRemoteCandidates.add(candidate)
                    Log.d(TAG, "Queued remote ICE candidate (remote desc not ready)")
                } else {
                    pc.addIceCandidate(candidate)
                }
            }

            socket?.on("call-ended") {
                Log.d(TAG, "Remote user ended the call")
                finishCallLocally()
            }

            socket?.on("call-failed") { args ->
                val data = args.firstOrNull() as? JSONObject
                Log.w(TAG, "Call failed: ${data?.optString("message")}")
                finishCallLocally()
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket init error", e)
        }
    }

    // ==========================================
    // MESSAGE SENDING
    // ==========================================

    fun sendMessage(
        clientMessageId: String,
        receiverUsername: String,
        content: String
    ): Boolean {
        if (socket?.connected() != true || !_registered.value) {
            Log.w(TAG, "Cannot send message: socket not registered")
            return false
        }
        return try {
            val data = JSONObject().apply {
                put("clientMessageId", clientMessageId)
                put("receiverUsername", receiverUsername)
                put("content", content)
            }
            socket?.emit("send-message", data)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Message send error", e)
            false
        }
    }

    fun acknowledgeMessage(
        clientMessageId: String,
        senderUsername: String
    ): Boolean {
        if (socket?.connected() != true || !_registered.value) return false
        return try {
            socket?.emit("message-delivered", JSONObject().apply {
                put("clientMessageId", clientMessageId)
                put("senderUsername", senderUsername)
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ack error", e)
            false
        }
    }

    // ==========================================
    // MAKE CALL
    // ==========================================

    fun makeCall(receiverUsername: String, receiverSocketId: String) {
        requestAudioFocus()
        createPeerConnection()
        // Caller expects their mic to be live immediately.
        localAudioTrack?.setEnabled(true)

        targetCallAddress = receiverSocketId.ifEmpty { receiverUsername }
        _callState.value = "RINGING"
        startCallTimeout()

        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)

                val offerJson = JSONObject().put("sdp", desc.description)
                socket?.emit("call-user", JSONObject().apply {
                    put("to", receiverUsername)
                    put("offer", offerJson)
                })
            }
        }, constraints)
    }

    // ==========================================
    // ANSWER CALL
    // ==========================================

    fun answerCall(fromSocketId: String) {
        targetCallAddress = fromSocketId
        requestAudioFocus()

        // User accepted – mark as connecting and unmute the mic.
        _callState.value = "CONNECTING"

        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        // Mic only goes live once the answer SDP is set.
                        localAudioTrack?.setEnabled(true)
                    }
                }, desc)

                val answerJson = JSONObject().put("sdp", desc.description)
                socket?.emit("answer-call", JSONObject().apply {
                    put("to", fromSocketId)
                    put("answer", answerJson)
                })
            }
        }, constraints)
    }

    // ==========================================
    // ICE CANDIDATE
    // ==========================================

    private fun sendIceCandidate(candidate: IceCandidate) {
        val target = targetCallAddress ?: return
        val jsonCandidate = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }
        socket?.emit("ice-candidate", JSONObject().apply {
            put("to", target)
            put("candidate", jsonCandidate)
        })
    }

    // ==========================================
    // END CALL
    // ==========================================

    fun endCall() {
        val target = targetCallAddress
        if (target != null) {
            socket?.emit("end-call", JSONObject().apply { put("to", target) })
        }
        finishCallLocally()
    }

    private fun finishCallLocally() {
        cancelCallTimeout()
        closePeerConnection()
        abandonAudioFocus()

        _incomingCall.value = null
        _incomingCallerName.value = null
        _callState.value = "IDLE"
    }

    // ==========================================
    // CALL TIMEOUT
    // ==========================================

    private fun startCallTimeout() {
        cancelCallTimeout()
        callTimeoutJob = scope.launch {
            delay(CALL_TIMEOUT_MS)
            // Only fires if we never reached CONNECTED.
            if (_callState.value == "RINGING" || _callState.value == "CONNECTING") {
                Log.w(TAG, "Call timed out")
                endCall()
            }
        }
    }

    private fun cancelCallTimeout() {
        callTimeoutJob?.cancel()
        callTimeoutJob = null
    }

    // ==========================================
    // SDP OBSERVER
    // ==========================================

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "SDP creation failed: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "SDP setting failed: $error")
        }
    }
}
