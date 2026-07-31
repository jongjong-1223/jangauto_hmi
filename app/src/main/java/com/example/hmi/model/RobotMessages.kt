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
 * Command to generate a ㄹ-shaped coverage path based on a polygon.
 */
data class GenerateCoveragePathRequest(
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("command") val command: String = "generate_coverage_path",
    @SerializedName("polygon") val polygon: List<Point>,
    @SerializedName("edge_safety_dist") val edgeSafetyDist: List<Double>,
    @SerializedName("robot_radius") val robotRadius: Double,
    @SerializedName("yaw_deg") val yawDeg: Double,
    @SerializedName("ridge_spacing") val ridgeSpacing: Double,
    @SerializedName("headland_length") val headlandLength: Double
) : RobotRequest

/**
 * Result of coverage path generation, containing two candidate paths.
 */
data class CoveragePathResult(
    @SerializedName("type") val type: String = "coverage_path_result",
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("paths") val paths: List<CoveragePath>
)

data class CoveragePath(
    @SerializedName("start_side") val startSide: String, // "left" or "right"
    @SerializedName("rect_length") val rectLength: Double,
    @SerializedName("rect_width") val rectWidth: Double,
    @SerializedName("work_len") val workLen: Double,
    @SerializedName("n_ridges") val nRidges: Int,
    @SerializedName("waypoints") val waypoints: List<Waypoint>
)

data class Waypoint(
    @SerializedName("x") val x: Double,
    @SerializedName("y") val y: Double,
    @SerializedName("kind") val kind: String // start, work_start, work_end, turn_out, turn_in, end
)

/**
 * Command to select one of the generated candidate paths.
 */
data class SelectCoveragePathRequest(
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("ref_msg_id") val refMsgId: String,
    @SerializedName("command") val command: String = "select_coverage_path",
    @SerializedName("path_index") val pathIndex: Int
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
    @SerializedName("tag_yaw_rate") val tagYawRate: Double? = null,
    @SerializedName("calibration_complete") val calibrationComplete: Boolean? = null,
    @SerializedName("path_selected") val pathSelected: Boolean? = null
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
