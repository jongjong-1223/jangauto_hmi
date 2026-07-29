package com.example.hmi.model

import com.google.gson.annotations.SerializedName

/**
 * Base interface for all messages sent to the robot.
 */
interface RobotRequest

/**
 * Generic Ack sent by the app for reliable downlink messages.
 */
data class AppAck(
    @SerializedName("type") val type: String = "app_ack",
    @SerializedName("msg_id") val msgId: String
) : RobotRequest

/**
 * Periodic heartbeat and state update message.
 */
data class ControlRequest(
    @SerializedName("sw_bits") val swBits: Int,
    @SerializedName("key_bits") val keyBits: Int,
    @SerializedName("speed_bits") val speedBits: Int,
    @SerializedName("video_bit") val videoBit: Int,
    @SerializedName("safe_bit") val safeBit: Int
) : RobotRequest

/**
 * Command to move the robot to specific coordinates.
 */
data class MoveRequest(
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("command") val command: String = "move",
    @SerializedName("x") val x: Double,
    @SerializedName("y") val y: Double
) : RobotRequest

/**
 * Command to power off the Raspberry Pi.
 */
data class PoweroffRequest(
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("command") val command: String = "poweroff"
) : RobotRequest

/**
 * Command to generate a path.
 */
data class GeneratePathRequest(
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("command") val command: String = "generate_path",
    @SerializedName("map_min_x") val mapMinX: String,
    @SerializedName("map_max_x") val mapMaxX: String,
    @SerializedName("map_top_y") val mapTopY: String,
    @SerializedName("map_bottom_y") val mapBottomY: String,
    @SerializedName("field_top_y") val fieldTopY: String,
    @SerializedName("field_bottom_y") val fieldBottomY: String,
    @SerializedName("crop_x") val cropX: String
) : RobotRequest

/**
 * Robot's current full status broadcast (Streaming).
 */
data class RobotStatus(
    @SerializedName("current_state") val state: String,
    @SerializedName("in_error") val inError: Boolean,
    @SerializedName("error_reason") val errorReason: String? = null,
    @SerializedName("tag_x") val tagX: Double? = null,
    @SerializedName("tag_y") val tagY: Double? = null,
    @SerializedName("tag_ori") val tagOri: Double? = null,
    @SerializedName("tag_vel") val tagVel: Double? = null,
    @SerializedName("tag_yaw_rate") val tagYawRate: Double? = null
)

/**
 * Vector Map Data received from the robot.
 */
data class MapData(
    @SerializedName("type") val type: String = "map_data",
    @SerializedName("msg_id") val msgId: String? = null,
    @SerializedName("obstacles") val obstacles: List<List<Point>>? = null,
    @SerializedName("map") val map: List<Point>? = null
)

data class Point(
    @SerializedName("x") val x: Double,
    @SerializedName("y") val y: Double
)

/**
 * Response from the robot for a move command.
 */
data class MoveAck(
    @SerializedName("type") val type: String = "move_ack",
    @SerializedName("msg_id") val msgId: String? = null,
    @SerializedName("accepted") val accepted: Boolean,
    @SerializedName("reason") val reason: String? = null
)
