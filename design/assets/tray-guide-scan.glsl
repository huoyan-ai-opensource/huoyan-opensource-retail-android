precision mediump float;
/** @resolution */
uniform vec2 u_resolution;
/** @time */
uniform float u_time;
/** @label Preview phase (-1 plays loop) @default -1 */
uniform float u_phase;

void main() {
  float t = u_phase < 0.0 ? mod(u_time, 4.8) : u_phase;
  float visible = smoothstep(2.5, 2.7, t) * (1.0 - smoothstep(3.9, 4.15, t));
  vec2 uv = gl_FragCoord.xy / u_resolution;
  float down = 1.0 - uv.y;
  float reach = smoothstep(2.6, 3.35, t);
  float halfWidth = 0.49 * down;
  float distanceToEdge = abs(uv.x - 0.5) - halfWidth;
  float inside = 1.0 - smoothstep(-0.01, 0.01, distanceToEdge);
  float rays = 1.0 - smoothstep(0.012, 0.035, abs(distanceToEdge));
  float reveal = 1.0 - smoothstep(reach - 0.1, reach, down);
  float bottomFade = 1.0 - smoothstep(0.91, 1.0, down);
  float alpha = visible * reveal * bottomFade * (inside * 0.11 + rays * 0.55);
  gl_FragColor = vec4(vec3(0.247, 0.243, 0.929) * alpha, alpha);
}
