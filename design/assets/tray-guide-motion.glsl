precision mediump float;
/** @resolution */
uniform vec2 u_resolution;
/** @time */
uniform float u_time;
/** @content */
uniform sampler2D u_content;
/** @label Layer: person=0, tray=1 @default 0 */
uniform float u_role;
/** @label Preview phase (-1 plays loop) @default -1 */
uniform float u_phase;

// 整个人物只采样一次；连续形变保留髋部、裤腿及鞋子的连接。
vec4 sampleArt(vec2 p) {
  vec2 uv = p / vec2(320.0, 200.0);
  float inside = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
  return texture2D(u_content, uv) * inside;
}

void main() {
  float t = u_phase < 0.0 ? mod(u_time, 4.8) : u_phase;
  float approach = smoothstep(0.5, 1.7, t);
  float place = smoothstep(1.7, 2.5, t);
  float visibility = smoothstep(0.0, 0.25, t) * (1.0 - smoothstep(4.2, 4.7, t));
  // 两步靠近：起步和停步收敛，左右脚交替摆动、抬脚。
  float walk = clamp((t - 0.5) / 1.2, 0.0, 1.0);
  float envelope = sin(3.14159265 * walk);
  float stride = sin(6.2831853 * walk);
  float bob = 1.4 * envelope * stride * stride;
  // 使用设计坐标，导出倍率不改变步幅；托盘跟随身体轻微起伏。
  vec2 position = gl_FragCoord.xy / u_resolution * vec2(320.0, 200.0);
  position -= vec2(24.0 * approach, bob - 14.0 * place * u_role);
  if (u_role < 0.5) {
    float down = 200.0 - position.y;
    // 腰髋保持完整；步幅从大腿向脚部平滑增加，不使用分割遮罩。
    float leg = smoothstep(118.0, 178.0, down);
    float side = 2.0 * smoothstep(83.0, 113.0, position.x) - 1.0;
    float swing = stride * envelope;
    // 最大横向梯度小于 1，防止映射折叠、断裂或重复采样。
    position.x -= 5.0 * side * swing * leg;
    position.y -= 3.0 * max(0.0, side * swing) * leg;
  }
  vec4 ink = sampleArt(position);
  gl_FragColor = ink * visibility;
}
