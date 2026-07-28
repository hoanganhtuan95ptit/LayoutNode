# 📖 Hướng Dẫn Sử Dụng `node-engine`

Thư viện **node-engine** giải quyết vấn đề measure & layout tốn kém trên UI thread bằng cách:

> **Mô tả layout bằng data class → Đo ở background thread → View chỉ vẽ kết quả đã tính sẵn.**

---

## Mục lục

1. [Bối cảnh & Vấn đề](#1-bối-cảnh--vấn-đề)
2. [Kiến trúc tổng quan](#2-kiến-trúc-tổng-quan)
3. [Cài đặt ban đầu](#3-cài-đặt-ban-đầu)
4. [Các node có sẵn](#4-các-node-có-sẵn)
   - [TextNode](#41-textnode----văn-bản)
   - [ImageNode](#42-imagenode----hình-ảnh)
   - [LinearNode](#43-linearnode----container-xếp-tuần-tự)
   - [ConstraintNode](#44-constraintnode----layout-theo-constraint)
   - [OutlineNode](#45-outlinenode----viền-bo-gócloading)
   - [ProgressBarNode](#46-progressbarnode----thanh-tiến-độ-ngang)
   - [LineNode](#47-linenode----đường-kẻ)
   - [SpaceNode](#48-spacenode----khoảng-trống)
   - [FlexboxNode](#49-flexboxnode----container-wrap-kiểu-flexboxlayout)
   - [EdgeInsets](#410-edgeinsets----paddingmargin)
   - [LayoutDimension](#411-layoutdimension----widthheight-kiểu-xml)
5. [Đo layout với LayoutEngine](#5-đo-layout-với-layoutengine)
6. [Gắn vào View](#6-gắn-vào-view)
7. [Luồng ViewModel → Fragment](#7-luồng-viewmodel--fragment)
8. [Dùng trong RecyclerView](#8-dùng-trong-recyclerview)
9. [Ví dụ đầy đủ](#9-ví-dụ-đầy-đủ)
10. [Mở rộng thêm node mới](#10-mở-rộng-thêm-node-mới)
11. [Hạn chế & khi nào KHÔNG nên dùng](#11-hạn-chế--khi-nào-không-nên-dùng)
12. [Hướng phát triển tiếp theo](#12-hướng-phát-triển-tiếp-theo)
13. [Tham khảo nhanh](#13-tham-khảo-nhanh)

---

## 1. Bối cảnh & Vấn đề

Trên Android stock, `View.onMeasure()` chạy ở **UI thread**, bao gồm cả text measurement (thao tác đắt nhất). Hệ quả:

- `wrap_content` lan ngược — mỗi lần dữ liệu thay đổi, đo lại cả cây.
- RecyclerView bind item → measure ngay tại frame hiển thị → **drop frame / janky**.

**Điều kiện thuận lợi trong app này:**

- Biết trước `maxWidth` (full screen / list item full width).
- Data tĩnh trong nhiều giây/phút.
- View self-contained, không phụ thuộc sibling.

→ Có thể **đo 1 lần ở background, cache kết quả, view chỉ vẽ**.

---

## 2. Kiến trúc tổng quan

`node-engine` tách UI thành 3 chặng rõ ràng:

1. **Build tree**: app tạo cây `LayoutNode` thuần data từ ViewModel/Repository.
2. **Precompute**: `LayoutEngine` đo cây đó ở background thread và trả về `DrawSpec`.
3. **Render**: `PrecomputedView` nhận `DrawSpec`, báo size cho Android rồi chỉ uỷ thác `draw(canvas)`.

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────┐
│   LayoutNode    │───▶│   LayoutEngine   │───▶│  MeasurePolicy  │───▶│   DrawSpec   │
│  (thuần data)   │    │  (orchestrate +  │    │ (thuật toán đo) │    │ (size + draw)│
│                 │    │      cache)      │    │                 │    │              │
└─────────────────┘    └──────────────────┘    └─────────────────┘    └──────────────┘
        ▲                                                                    │
        │                                                                    ▼
 ViewModel /                                                      ┌──────────────────────┐
 Repository                                                       │ PrecomputedView       │
                                                                  │ + PrecomputedDelegate │
                                                                  └──────────────────────┘
   bất kỳ thread                         background thread                 UI thread
```

| Tầng | Trách nhiệm | Thread |
|------|-------------|--------|
| `LayoutNode` | Mô tả layout bằng data immutable: text, ảnh, padding, width/height, click callback, children... | bất kỳ |
| `LayoutEngine.measure()` | Entry point đo layout: chặn main thread, tạo `MeasureContext`, chạy đo root và gắn cache slot nếu caller truyền `id` | background |
| `MeasureContext` | Quản lý đo child và reuse `DrawSpec` qua cache theo `LayoutNode.id` | background |
| `MeasurePolicy<N>` | Thuật toán đo cụ thể cho từng loại node: resolve constraint, tính size, gán vị trí child, tạo `DrawSpec` tương ứng | background |
| `DrawSpec` | Kết quả đã đo: `left/top/width/height`, logic `draw(canvas)`, hit-test, lifecycle attach/detach | hand-off sang UI |
| `PrecomputedView` / `PrecomputedDelegate` | Giữ spec hiện tại, `requestLayout()` khi size đổi, `invalidate()` khi chỉ cần vẽ lại, dispatch tap tới node clickable | UI |

Nói ngắn gọn: `LayoutNode` là **data**, `MeasurePolicy` là **behavior đo**, còn `DrawSpec` là **kết quả renderable**. `LayoutEngine` không cần biết chi tiết `TextNode`, `ImageNode`, `LinearNode`...; nó chỉ điều phối quá trình đo và cache. Mỗi node tự gọi policy tương ứng, ví dụ `TextNode` gọi `TextMeasurePolicy`, `ImageNode` gọi `ImageMeasurePolicy`, `FlexboxNode` gọi `FlexboxMeasurePolicy`.

Luồng update thường gặp:

```kotlin
// background thread
val spec = LayoutEngine.measure(node, Constraints(maxWidth), id = cacheKey)

// main thread
precomputedView.spec = spec
```

Cache hoạt động theo `LayoutNode.id`: nếu node mới vẫn là **cùng instance** (`===`) và spec cũ còn hợp lệ dưới constraint mới, engine trả lại spec cũ để bỏ qua phần đo tốn kém như record text `Picture`, tính intrinsic ảnh, layout container... Nếu dữ liệu đổi hoặc constraint không còn phù hợp, node được đo lại như bình thường.

Vì `DrawSpec` có thể giữ tài nguyên nặng (`Picture`, `Drawable`, `Bitmap`, animator state), caller nên gọi `LayoutEngine.evict(id)` khi màn hình/view gắn với cache key bị huỷ hẳn, hoặc `LayoutEngine.clearCache()` khi đổi theme/density hay low-memory.

**Quy tắc vàng:** mọi resource phải resolve sẵn trước khi đưa vào engine — `Bitmap` đã decode, `color` là `Int`, `Typeface` đã load. Engine **không được đụng `Context`**.

---

## 3. Cài đặt ban đầu

### 3.1 Thêm module

`settings.gradle.kts`:

```kotlin
include(":node-engine")
include(":glide-loader") // nếu muốn dùng implementation Glide có sẵn
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":node-engine"))
    implementation(project(":glide-loader")) // cung cấp GlideImageLoader + transforms
}
```

Nếu dùng qua Maven Local, thêm `mavenLocal()` vào repositories của project tiêu thụ rồi khai báo:

```kotlin
dependencies {
        implementation("com.github.hoanganhtuan95ptit.LayoutNode:node-engine:1.2.1.10")
    implementation("com.github.hoanganhtuan95ptit.LayoutNode:glide-loader:1.2.1.10")
}
```

Publish cả hai artifact lên Maven Local:

```bash
./gradlew publishLibrariesToMavenLocal
```

Hoặc publish riêng module Glide:

```bash
./gradlew :glide-loader:publishReleasePublicationToMavenLocal
```

### 3.2 Cài ImageLoader (bắt buộc nếu dùng ảnh async)

Gọi **một lần duy nhất** trong `Application.onCreate()`:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // GlideImageLoader nằm trong module :glide-loader
        ImageLoader.install(GlideImageLoader(this))
    }
}
```

> ⚠️ **Quan trọng:** Nếu không `install` `ImageLoader`, mọi `ImageNode` với nguồn
> `ResSource` / `UrlSource` / `PathSource` / `DrawableSource` sẽ **không hiển thị ảnh**.
> Với `BitmapSource` (bitmap đã load sẵn) thì không cần loader.

---

## 4. Các node có sẵn

### 4.1 `TextNode` — Văn bản

```kotlin
TextNode(
    text           = BigText("Hello World"),
    textSizePx     = 18f * resources.displayMetrics.scaledDensity, // sp → px
    color          = Color.BLACK,
    maxLines       = 2,
    typeface       = Typeface.DEFAULT_BOLD,   // null = hệ thống
    lineSpacingMul = 1.2f,
    lineSpacingAdd = 0f,
    padding        = EdgeInsets.all(8.dp),
    layoutWidth    = LayoutDimension.WrapContent,
    layoutHeight   = LayoutDimension.WrapContent
)
```

| Tham số | Kiểu | Bắt buộc | Ghi chú |
|---------|------|:--------:|---------|
| `text` | `BigText` | ✅ | Hỗ trợ big text qua `BigStyle` |
| `textSizePx` | `Float` | ✅ | Đơn vị **pixel** — tự quy đổi `sp → px` |
| `color` | `Int` | ✅ | `Color.BLACK`, `0xFF334455.toInt()`, v.v. |
| `maxLines` | `Int` | | Mặc định không giới hạn (`Int.MAX_VALUE`) |
| `typeface` | `Typeface?` | | Đã load sẵn; engine không đụng `Context` |
| `lineSpacingMul` | `Float` | | Mặc định `1f` |
| `lineSpacingAdd` | `Float` | | Mặc định `0f` |
| `padding` | `EdgeInsets` | | Mặc định `EdgeInsets.ZERO` |
| `layoutWidth` | `LayoutDimension` | | Mặc định `WrapContent` |
| `layoutHeight` | `LayoutDimension` | | Mặc định `WrapContent` |

Khi vượt quá `maxLines`, text tự động bị ellipsize (`…`) ở cuối.

---

### 4.2 `ImageNode` — Hình ảnh

**Bitmap có sẵn (đơn giản nhất, không cần ImageLoader):**

```kotlin
ImageNode.fromBitmap(
    bitmap       = myBitmap,
    layoutWidth  = LayoutDimension.Fixed(48.dp),
    layoutHeight = LayoutDimension.Fixed(48.dp),
    padding      = EdgeInsets.ZERO
)
```

**Từ drawable resource:**

```kotlin
ImageNode(
    source       = BigImage.ResSource(R.drawable.ic_logo),
    layoutWidth  = LayoutDimension.Fixed(48.dp),
    layoutHeight = LayoutDimension.Fixed(48.dp),
    padding      = EdgeInsets(right = 8.dp)
)
```

**Từ URL:**

```kotlin
ImageNode(
    source       = BigImage.UrlSource("https://example.com/photo.jpg"),
    layoutWidth  = LayoutDimension.Fixed(200.dp),
    layoutHeight = LayoutDimension.Fixed(120.dp)
)
```

**Từ Drawable object:**

```kotlin
ImageNode(
    source       = BigImage.DrawableSource(myDrawable),
    layoutWidth  = LayoutDimension.Fixed(24.dp),
    layoutHeight = LayoutDimension.Fixed(24.dp)
)
```

> ⚠️ Với source async (`ResSource` / `UrlSource` / `PathSource`), engine cần biết
> kích thước trước khi ảnh load xong: đặt `layoutWidth` / `layoutHeight` thành
> `LayoutDimension.Fixed(...)` hay `LayoutDimension.MatchParent` trên trục đã có
> constraint hữu hạn.

Trong khi bitmap chưa load xong, `ImageSpec` vẫn **chiếm đúng kích thước** và không vẽ gì. Khi bitmap về, view tự `postInvalidateOnAnimation()`.

Khi đã có ảnh, `ImageSpec` vẽ theo kiểu **CenterInside** trong vùng content:
giữ tỉ lệ ảnh, canh giữa trong phần còn lại sau `padding`, và chỉ scale xuống
nếu ảnh lớn hơn khung đo.

---

### 4.3 `LinearNode` — Container xếp tuần tự

```kotlin
// Hàng ngang
LinearNode(
    orientation = Orientation.HORIZONTAL,
    children    = listOf(iconNode, textNode),
    gap         = 8.dp,
    crossAlign  = CrossAlign.CENTER,     // căn giữa theo chiều dọc
    padding     = EdgeInsets.symmetric(h = 16.dp, v = 12.dp)
)

// Hàng dọc
LinearNode(
    orientation = Orientation.VERTICAL,
    children    = listOf(titleNode, subtitleNode, imageNode),
    gap         = 4.dp,
    crossAlign  = CrossAlign.START
)
```

| Tham số | Kiểu | Ghi chú |
|---------|------|---------|
| `orientation` | `Orientation` | `HORIZONTAL` / `VERTICAL` |
| `children` | `List<LayoutNode>` | Danh sách con theo thứ tự |
| `gap` | `Int` | Khoảng cách **pixel** giữa các children trên trục chính |
| `crossAlign` | `CrossAlign` | `START` / `CENTER` / `END` trên trục phụ |
| `padding` | `EdgeInsets` | Padding bao ngoài toàn bộ container |
| `layoutWidth` / `layoutHeight` | `LayoutDimension` | `WrapContent` / `MatchParent` / `Fixed(px)` |

**Thuật toán đo 2 pass:**
1. **Pass 1** — đo tất cả children để biết cross-size lớn nhất.
2. **Pass 2** — gán vị trí với `crossAlign` qua `DrawSpec.withPosition()`.

---

### 4.4 `ConstraintNode` — Layout theo constraint

Tương tự `ConstraintLayout` trong XML. Linh hoạt hơn `LinearNode` khi cần neo các phần tử vào nhau theo nhiều chiều.

```kotlin
val PARENT = ConstraintNode.PARENT

ConstraintNode(
    padding  = EdgeInsets.all(16.dp),
    children = listOf(
        ConstraintChild(
            id             = "avatar",
            node           = ImageNode(
                BigImage.ResSource(R.drawable.avatar),
                layoutWidth = LayoutDimension.Fixed(40.dp),
                layoutHeight = LayoutDimension.Fixed(40.dp),
            ),
            startToStartOf = PARENT,  marginStart = 0,
            topToTopOf     = PARENT,  marginTop   = 0,
        ),
        ConstraintChild(
            id           = "name",
            node         = TextNode(BigText("Nguyễn Văn A"), 16.sp, Color.BLACK, typeface = Typeface.DEFAULT_BOLD),
            startToEndOf = "avatar", marginStart = 12.dp,
            endToEndOf   = PARENT,
            topToTopOf   = "avatar",
            width        = LayoutDimension.MatchParent,
        ),
        ConstraintChild(
            id             = "email",
            node           = TextNode(BigText("nva@example.com"), 13.sp, Color.GRAY),
            startToStartOf = "name",
            topToBottomOf  = "name", marginTop = 4.dp,
        ),
    )
)
```

#### Kích thước child

| Giá trị | Ý nghĩa |
|---------|---------|
| `LayoutDimension.WrapContent` | *(Mặc định)* Chiều rộng/cao theo nội dung |
| `LayoutDimension.MatchParent` | Lấp đầy khoảng khả dụng theo anchors. Có đủ 2 anchor cùng trục thì tương đương `0dp` trong XML ConstraintLayout. |
| `LayoutDimension.Fixed(px)` | Cố định giá trị pixel |

> `ConstraintChild.width` / `height` dùng cùng `LayoutDimension` với mọi `LayoutNode`.
> Điểm khác là `MatchParent` ở đây fill theo khoảng anchor khả dụng của child.

#### Constraint ngang

| Thuộc tính | Nghĩa |
|-----------|-------|
| `startToStartOf = "id"` | Cạnh trái của this = cạnh trái của target |
| `startToEndOf = "id"` | Cạnh trái của this = cạnh phải của target |
| `endToEndOf = "id"` | Cạnh phải của this = cạnh phải của target |
| `endToStartOf = "id"` | Cạnh phải của this = cạnh trái của target |

#### Constraint dọc

| Thuộc tính | Nghĩa |
|-----------|-------|
| `topToTopOf = "id"` | Cạnh trên của this = cạnh trên của target |
| `topToBottomOf = "id"` | Cạnh trên của this = cạnh dưới của target |
| `bottomToBottomOf = "id"` | Cạnh dưới của this = cạnh dưới của target |
| `bottomToTopOf = "id"` | Cạnh dưới của this = cạnh trên của target |

#### Margin & Bias

```kotlin
ConstraintChild(
    id               = "btn",
    node             = myNode,
    startToStartOf   = PARENT,
    endToEndOf       = PARENT,
    topToTopOf       = PARENT,
    bottomToBottomOf = PARENT,
    horizontalBias   = 0.5f,   // 0f = hugged start, 1f = hugged end, 0.5f = center
    verticalBias     = 0.5f,   // chỉ có hiệu lực khi CẢ 2 constraint cùng trục được set
    marginStart      = 16.dp,
    marginEnd        = 16.dp,
    marginTop        = 8.dp,
    marginBottom     = 8.dp,
)
```

> 💡 Dùng `ConstraintNode.PARENT` (= `"parent"`) để trỏ đến container.

**Thuật toán:** Iterative topological sort — mỗi vòng lặp giải những child mà tất cả target ID đã resolved. Worst case O(n²) với n children — phù hợp cho < 50 views trong 1 màn.

**Hạn chế hiện tại:**
- Không hỗ trợ chain (horizontal/vertical chain).
- Không hỗ trợ Guideline / Barrier.
- Chiều cao container = wrap-to-content (max bottom của children + padding).
- Dependency cycle → child bị đặt tại (0, 0) làm fallback.

---

### 4.5 `OutlineNode` — Viền bo góc/loading

`OutlineNode` chỉ vẽ effect trong bounds đã đo; nó không chứa và không measure child.
Muốn phủ effect lên content, đặt `OutlineNode` như một sibling cuối trong `ConstraintNode`
hoặc container tương đương. Logic chuyển từ `OutlineDelegate`: bo góc, dash,
trạng thái `IDLE` / `LOADING` / `HIDDEN`, và animation segment chạy quanh path.

```kotlin
OutlineNode(
    layoutWidth = LayoutDimension.MatchParent,
    layoutHeight = LayoutDimension.Fixed(72.dp),
    backgroundColor = 0x11E91E63,
    strokeColor = 0xFFE91E63.toInt(),
    strokeWidth = 2.dp.toFloat(),
    cornerRadius = 16.dp.toFloat(),
    dashWidth = 10.dp.toFloat(),
    dashGap = 6.dp.toFloat(),
    loadingSegmentRatio = 0.35f,
    loadingDurationMs = 900L,
    state = OutlineState.LOADING
)
```

| Tham số | Ghi chú |
|---------|---------|
| `layoutWidth` / `layoutHeight` | Size của vùng effect; nên dùng `Fixed` hoặc `MatchParent` trong constraint hữu hạn |
| `padding` | Inset path vào trong bounds, không phải padding cho child |
| `backgroundColor` | Màu fill rounded background, mặc định `Color.TRANSPARENT`; vẽ trước outline |
| `strokeColor` / `strokeWidth` | Màu và độ dày nét |
| `cornerRadius` | Bán kính bo góc, tự clamp theo bounds |
| `dashWidth` / `dashGap` | Nếu cả hai > 0 thì dùng `DashPathEffect` |
| `loadingSegmentRatio` | Độ dài segment loading theo tỉ lệ path, clamp trong `0.05f..1f` |
| `loadingDurationMs` | Thời gian segment chạy hết một vòng |
| `state` | `OutlineState.IDLE` vẽ full outline, `LOADING` vẽ segment chạy, `HIDDEN` ẩn outline |

Sau khi đã gán spec cho `PrecomputedView`, nếu giữ được reference kiểu `OutlineSpec`,
có thể đổi trạng thái động:

```kotlin
outlineSpec.setLoading(loading = true, show = true, animate = true)
outlineSpec.setState(OutlineState.HIDDEN, animate = true)
```

Animation tự start khi `PrecomputedView` attach và tự dừng khi detach; redraw dùng
`postInvalidateOnAnimation()`.

Nếu dùng `backgroundColor` opaque trong overlay, đặt `OutlineNode` trước content để
nền nằm dưới chữ. Nếu đặt nó sau content để stroke phủ lên trên, nên dùng
background trong suốt hoặc có alpha.

---

### 4.6 `ProgressBarNode` — Thanh tiến độ ngang

`ProgressBarNode` vẽ một progress bar ngang dạng rounded-rect: track nền trước,
progress fill sau. Node này không đọc resource, nên màu phải là `Int ARGB` đã
resolve sẵn. Vì `WrapContent` không có intrinsic height cho progress bar, nên
ví dụ thực tế nên truyền `layoutHeight = LayoutDimension.Fixed(...)`.

```kotlin
LinearNode(
    orientation = Orientation.VERTICAL,
    gap = 8.dp,
    padding = EdgeInsets.all(16.dp),
    layoutWidth = LayoutDimension.MatchParent,
    children = listOf(
        TextNode(
            text = BigText("Listening accuracy  68/100"),
            textSizePx = 14.sp,
            color = 0xFF202124.toInt(),
            typeface = Typeface.DEFAULT_BOLD,
            maxLines = 1
        ),
        ProgressBarNode(
            progress = 68,
            max = 100,
            trackColor = 0xFFE8EAED.toInt(),
            progressColor = 0xFF1B998B.toInt(),
            layoutWidth = LayoutDimension.MatchParent,
            layoutHeight = LayoutDimension.Fixed(8.dp)
        )
    )
)
```

| Tham số | Ghi chú |
|---------|---------|
| `progress` / `max` | `progress` được clamp trong `0..max`; `max` tối thiểu là `1` |
| `trackColor` | Màu nền của toàn bộ bar |
| `progressColor` | Màu phần đã hoàn thành |
| `cornerRadius` | `< 0` nghĩa là pill tự lấy `height / 2`; truyền `0f` để vẽ vuông |
| `layoutWidth` | Thường dùng `MatchParent` trong parent có constraint hữu hạn |
| `layoutHeight` | Nên dùng `Fixed(px)` để tránh bar cao `0px` khi wrap-content |

---

### 4.7 `LineNode` — Đường kẻ

`LineNode` vẽ một đường ngang hoặc dọc, có thể dùng nét liền hoặc nét đứt.
Node này phù hợp cho divider, dấu phân tách, hoặc phần line trang trí như
ví dụ text căn giữa bên dưới một đường dashed.

```kotlin
LinearNode(
    orientation = Orientation.VERTICAL,
    crossAlign = CrossAlign.CENTER,
    gap = 12.dp,
    padding = EdgeInsets(top = 14.dp),
    layoutWidth = LayoutDimension.MatchParent,
    layoutHeight = LayoutDimension.Fixed(86.dp),
    children = listOf(
        LineNode(
            color = 0xFFB8B8B8.toInt(),
            strokeWidth = 2.dp.toFloat(),
            dashWidth = 6.dp.toFloat(),
            dashGap = 6.dp.toFloat(),
            layoutWidth = LayoutDimension.Fixed(240.dp),
            layoutHeight = LayoutDimension.Fixed(2.dp)
        ),
        TextNode(
            text = BigText("rất vui được quen biết bạn"),
            textSizePx = 20.sp,
            color = 0xFF202124.toInt(),
            maxLines = 1
        )
    )
)
```

| Tham số | Ghi chú |
|---------|---------|
| `orientation` | `Orientation.HORIZONTAL` mặc định; truyền `VERTICAL` để vẽ đường dọc |
| `strokeWidth` | Độ dày nét theo px |
| `dashWidth` / `dashGap` | Nếu cả hai > 0 thì vẽ nét đứt |
| `layoutWidth` / `layoutHeight` | Nên dùng `Fixed` hoặc `MatchParent` để line có chiều dài rõ ràng |

---

### 4.8 `SpaceNode` — Khoảng trống

`SpaceNode` chỉ chiếm kích thước trong layout, không vẽ gì. Dùng nó khi cần
chèn khoảng trống riêng lẻ giữa vài child, thay vì tăng `gap` cho toàn bộ
`LinearNode`.

```kotlin
LinearNode(
    orientation = Orientation.HORIZONTAL,
    crossAlign = CrossAlign.CENTER,
    children = listOf(
        TextNode(BigText("Left"), 14.sp, Color.BLACK),
        SpaceNode.horizontal(12.dp),
        TextNode(BigText("Right"), 14.sp, Color.BLACK)
    )
)

LinearNode(
    orientation = Orientation.VERTICAL,
    children = listOf(
        titleNode,
        SpaceNode.vertical(8.dp),
        subtitleNode
    )
)
```

Nếu cần kiểm soát trực tiếp bằng `LayoutDimension`, truyền vào như các node khác:

```kotlin
SpaceNode(
    layoutWidth = LayoutDimension.MatchParent,
    layoutHeight = LayoutDimension.Fixed(16.dp)
)
```

| Cách tạo | Ghi chú |
|----------|---------|
| `SpaceNode.horizontal(widthPx)` | Khoảng trống theo trục ngang, height = `0` |
| `SpaceNode.vertical(heightPx)` | Khoảng trống theo trục dọc, width = `0` |
| `SpaceNode.fixed(widthPx, heightPx)` | Khoảng trống cố định cả hai chiều |
| `SpaceNode(layoutWidth, layoutHeight)` | Dùng `WrapContent` / `MatchParent` / `Fixed(px)` trực tiếp |

---

### 4.9 `FlexboxNode` — Container wrap kiểu FlexboxLayout

`FlexboxNode` là container tự đo giống nhóm API chính của Google
`FlexboxLayout`: có direction, wrap, justify, align theo line và theo item.
Node này vẫn trả về `GroupSpec`, nên không cần sửa `LayoutEngine` hay
`PrecomputedView`.

```kotlin
FlexboxNode(
    flexDirection = FlexDirection.ROW,
    flexWrap = FlexWrap.WRAP,
    justifyContent = FlexJustifyContent.SPACE_BETWEEN,
    alignItems = FlexAlignItems.CENTER,
    alignContent = FlexAlignContent.FLEX_START,
    gap = 8.dp,
    padding = EdgeInsets.all(16.dp),
    layoutWidth = LayoutDimension.MatchParent,
    children = listOf(
        FlexChild(
            node = TextNode(
                text = BigText("Kotlin"),
                textSizePx = 14.sp,
                color = Color.WHITE,
                padding = EdgeInsets.symmetric(h = 10.dp, v = 6.dp)
            )
        ),
        FlexChild(
            node = TextNode(BigText("Android"), 14.sp, Color.WHITE),
            wrapBefore = false
        )
    )
)
```

| Tham số container | Ghi chú |
|-------------------|---------|
| `flexDirection` | `ROW` / `ROW_REVERSE` / `COLUMN` / `COLUMN_REVERSE` |
| `flexWrap` | `NOWRAP` / `WRAP` / `WRAP_REVERSE` |
| `justifyContent` | `FLEX_START`, `FLEX_END`, `CENTER`, `SPACE_BETWEEN`, `SPACE_AROUND`, `SPACE_EVENLY` |
| `alignItems` | `FLEX_START`, `FLEX_END`, `CENTER`, `STRETCH` |
| `alignContent` | Căn các line trong trục phụ; hỗ trợ cả `STRETCH` |
| `gap` | Khoảng cách chung giữa item và line |
| `mainGap` / `crossGap` | Override riêng khoảng cách trục chính / trục phụ |
| `padding` | Padding bao ngoài toàn bộ flex container |
| `layoutWidth` / `layoutHeight` | Dùng chung `LayoutDimension` của engine |

| Tham số `FlexChild` | Ghi chú |
|---------------------|---------|
| `node` | `LayoutNode` con |
| `order` | Sắp xếp trước khi layout, giống FlexboxLayout |
| `flexGrow` | Chia phần trống còn lại trên line |
| `flexShrink` | Co bớt khi line `NOWRAP` vượt quá kích thước |
| `alignSelf` | Override `alignItems` cho từng child |
| `flexBasisPercent` | Tỉ lệ kích thước theo trục chính nếu parent có constraint hữu hạn |
| `wrapBefore` | Buộc child bắt đầu line mới |

Lưu ý: baseline alignment chưa hỗ trợ vì `DrawSpec` hiện không expose baseline
chung cho mọi node. `flexGrow`, `flexShrink`, `STRETCH` đổi measured box qua
`DrawSpec.withSize()`; nếu child là một composite node phức tạp, phần nội dung
bên trong không tự remeasure lại theo size mới.

---

### 4.10 `EdgeInsets` — Padding/Margin

```kotlin
EdgeInsets.ZERO                                     // không padding
EdgeInsets.all(16.dp)                               // 16dp mọi phía
EdgeInsets.symmetric(h = 16.dp, v = 8.dp)          // 16dp ngang, 8dp dọc
EdgeInsets(left = 8.dp, top = 4.dp,
           right = 8.dp, bottom = 4.dp)             // custom từng phía
```

---

### 4.11 `LayoutDimension` — Width/Height kiểu XML

Mọi `LayoutNode` đều có:

```kotlin
layoutWidth  = LayoutDimension.WrapContent
layoutHeight = LayoutDimension.WrapContent
```

| Giá trị | Tương đương XML | Hành vi |
|---------|-----------------|---------|
| `LayoutDimension.WrapContent` | `wrap_content` | Lấy kích thước nội dung, tối đa bằng parent constraints |
| `LayoutDimension.MatchParent` | `match_parent` | Lấp đầy constraint hữu hạn của parent; nếu trục đang unbounded thì fallback về wrap-content |
| `LayoutDimension.Fixed(px)` | `48dp`, `120px`, ... | Cố định pixel, vẫn bị chặn bởi parent constraints |

Ví dụ:

```kotlin
TextNode(
    text = BigText("Title"),
    textSizePx = 16.sp,
    color = Color.BLACK,
    layoutWidth = LayoutDimension.MatchParent,
)

LinearNode(
    orientation = Orientation.VERTICAL,
    children = listOf(title, subtitle),
    layoutWidth = LayoutDimension.Fixed(280.dp),
    layoutHeight = LayoutDimension.WrapContent,
)
```

Nếu nội dung lớn hơn size cuối cùng, `DrawSpec` sẽ clip trong bounds đã đo.

---

## 5. Đo layout với LayoutEngine

```kotlin
// Bắt buộc gọi từ background thread
val spec: DrawSpec = withContext(Dispatchers.Default) {
    LayoutEngine.measure(
        node        = myLayoutNode,
        constraints = Constraints(maxWidth = containerWidthPx)
    )
}
```

> ✅ `LayoutEngine.measure()` không đụng bất kỳ UI thread API nào — an toàn hoàn toàn trên background thread.

**Lấy `maxWidth`:**

```kotlin
// Cách 1: Width màn hình
val widthPx = resources.displayMetrics.widthPixels

// Cách 2: Width chính xác sau khi view đã layout
binding.container.doOnPreDraw {
    val widthPx = binding.container.width
    viewModel.load(widthPx)
}
```

**`Constraints`:**

```kotlin
Constraints(maxWidth = 800)                        // maxHeight = Int.MAX_VALUE (unbounded)
Constraints(maxWidth = 400, maxHeight = 200)       // giới hạn cả 2 chiều
```

---

## 6. Gắn vào View

### 6.1 Khai báo XML

```xml
<com.simple.phonetics.ui.precompute.PrecomputedView
    android:id="@+id/precomputedView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

> 💡 Nên dùng `layout_width="match_parent"` để view có width cố định, giúp `doOnPreDraw` lấy width chính xác.

### 6.2 Gán spec

```kotlin
// Luôn gán trên UI thread
binding.precomputedView.spec = drawSpec

// Xoá (trả về kích thước 0x0)
binding.precomputedView.spec = null
```

`PrecomputedView.spec` setter tự động xử lý:

| Tình huống | Hành vi |
|-----------|---------|
| Kích thước thay đổi | `requestLayout()` → `postInvalidateOnAnimation()` |
| Kích thước giữ nguyên | `postInvalidateOnAnimation()` |
| View đang attached | `onDetachedFromWindow(old)` → `onAttachedToWindow(new)` |
| Spec mới có `ImageSpec` async | Tự bắt đầu load bitmap qua `ImageLoader` |

---

## 7. Luồng ViewModel → Fragment

### ViewModel

```kotlin
class CardViewModel(private val appContext: Context) : ViewModel() {

    private val _spec = MutableStateFlow<DrawSpec?>(null)
    val spec: StateFlow<DrawSpec?> = _spec.asStateFlow()

    fun loadCard(word: String, ipa: String, widthPx: Int) {
        viewModelScope.launch {
            // 1. Build node (bất kỳ thread nào — thuần data)
            val node = buildCardNode(word, ipa)

            // 2. Đo trên background thread
            val result = withContext(Dispatchers.Default) {
                LayoutEngine.measure(node, Constraints(widthPx))
            }

            // 3. Emit sang UI
            _spec.value = result
        }
    }

    // Nếu gọi nhiều lần liên tiếp — cancel job cũ để không emit kết quả cũ
    private var measureJob: Job? = null

    fun loadCardCancellable(word: String, ipa: String, widthPx: Int) {
        measureJob?.cancel()
        measureJob = viewModelScope.launch {
            val node = buildCardNode(word, ipa)
            _spec.value = withContext(Dispatchers.Default) {
                LayoutEngine.measure(node, Constraints(widthPx))
            }
        }
    }

    private fun buildCardNode(word: String, ipa: String): LayoutNode = LinearNode(
        orientation = Orientation.VERTICAL,
        gap         = 4.dp,
        children    = listOf(
            TextNode(BigText(word), 18.sp, Color.BLACK, typeface = Typeface.DEFAULT_BOLD),
            TextNode(BigText(ipa),  14.sp, Color.GRAY)
        )
    )
}
```

### Fragment

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Lấy width sau khi view đã layout để đảm bảo chính xác
    binding.precomputedView.doOnPreDraw {
        viewModel.loadCard("Hello", "/həˈloʊ/", binding.precomputedView.width)
    }

    // Collect spec và gán cho view
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.spec.collect { spec ->
                binding.precomputedView.spec = spec
            }
        }
    }
}
```

---

## 8. Dùng trong RecyclerView

```kotlin
class WordAdapter(
    private val scope: CoroutineScope,
    private val itemWidthPx: Int
) : RecyclerView.Adapter<WordViewHolder>() {

    var items: List<WordItem> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val item = items[position]

        // 1. Cancel job đo của lần bind trước (tránh race condition khi scroll nhanh)
        holder.measureJob?.cancel()
        holder.binding.precomputedView.spec = null   // clear để tránh hiện nội dung cũ

        // 2. Đo async
        holder.measureJob = scope.launch {
            val node = buildWordNode(item)
            val spec = withContext(Dispatchers.Default) {
                LayoutEngine.measure(node, Constraints(itemWidthPx))
            }
            // 3. Gán kết quả (launch đảm bảo luôn chạy trên Main)
            holder.binding.precomputedView.spec = spec
        }
    }

    override fun onViewRecycled(holder: WordViewHolder) {
        super.onViewRecycled(holder)
        holder.measureJob?.cancel()
        holder.binding.precomputedView.spec = null
    }

    private fun buildWordNode(item: WordItem): LayoutNode = LinearNode(
        orientation = Orientation.HORIZONTAL,
        gap         = 8.dp,
        crossAlign  = CrossAlign.CENTER,
        children    = listOf(
            ImageNode(
                BigImage.ResSource(item.iconRes),
                layoutWidth = LayoutDimension.Fixed(24.dp),
                layoutHeight = LayoutDimension.Fixed(24.dp),
            ),
            LinearNode(
                orientation = Orientation.VERTICAL,
                gap         = 2.dp,
                children    = listOf(
                    TextNode(BigText(item.word), 16.sp, Color.BLACK, maxLines = 1,
                             typeface = Typeface.DEFAULT_BOLD),
                    TextNode(BigText(item.ipa),  13.sp, Color.GRAY,  maxLines = 1)
                )
            )
        ),
        padding = EdgeInsets.symmetric(h = 16.dp, v = 12.dp)
    )
}

class WordViewHolder(val binding: ItemWordBinding) : RecyclerView.ViewHolder(binding.root) {
    var measureJob: Job? = null
}
```

---

## 9. Ví dụ đầy đủ

### Ví dụ 1: Profile Card (ConstraintNode)

Avatar tròn bên trái, tên + email xếp dọc bên phải, nút action góc phải:

```kotlin
fun buildProfileCard(
    context: Context,
    name: String,
    email: String,
    avatarRes: Int,
    widthPx: Int
): LayoutNode {
    val dm = context.resources.displayMetrics
    fun Int.dp() = (this * dm.density).toInt()
    fun Float.sp() = this * dm.scaledDensity
    val PARENT = ConstraintNode.PARENT

    return ConstraintNode(
        padding  = EdgeInsets.all(16.dp()),
        children = listOf(
            // Avatar
            ConstraintChild(
                id             = "avatar",
                node           = ImageNode(
                    BigImage.ResSource(avatarRes),
                    layoutWidth = LayoutDimension.Fixed(48.dp()),
                    layoutHeight = LayoutDimension.Fixed(48.dp()),
                ),
                startToStartOf = PARENT,
                topToTopOf     = PARENT,
            ),
            // Tên
            ConstraintChild(
                id           = "name",
                node         = TextNode(
                    text       = BigText(name),
                    textSizePx = 16f.sp(),
                    color      = 0xFF111827.toInt(),
                    typeface   = Typeface.DEFAULT_BOLD,
                    maxLines   = 1
                ),
                startToEndOf = "avatar", marginStart = 12.dp(),
                endToEndOf   = PARENT,   marginEnd   = 0,
                topToTopOf   = "avatar",
                width        = LayoutDimension.MatchParent,
            ),
            // Email
            ConstraintChild(
                id             = "email",
                node           = TextNode(
                    text       = BigText(email),
                    textSizePx = 13f.sp(),
                    color      = 0xFF6B7280.toInt(),
                    maxLines   = 1
                ),
                startToStartOf = "name",
                endToEndOf     = "name",
                topToBottomOf  = "name", marginTop = 2.dp(),
                width          = LayoutDimension.MatchParent,
            ),
        )
    )
}

// Trong ViewModel
fun loadProfile(context: Context, widthPx: Int) {
    viewModelScope.launch {
        val node = buildProfileCard(context, "Nguyễn Văn A", "nva@example.com",
                                    R.drawable.avatar, widthPx)
        _spec.value = withContext(Dispatchers.Default) {
            LayoutEngine.measure(node, Constraints(widthPx))
        }
    }
}
```

---

### Ví dụ 2: Chip icon + text (LinearNode)

```kotlin
fun buildChip(
    bitmap: Bitmap,
    label: String,
    textSizePx: Float,
    textColor: Int,
    gapPx: Int,
    paddingH: Int,
    paddingV: Int
): LinearNode = LinearNode(
    orientation = Orientation.HORIZONTAL,
    crossAlign  = CrossAlign.CENTER,
    gap         = gapPx,
    padding     = EdgeInsets.symmetric(h = paddingH, v = paddingV),
    children    = listOf(
        ImageNode.fromBitmap(
            bitmap,
            layoutWidth = LayoutDimension.Fixed(20.dp),
            layoutHeight = LayoutDimension.Fixed(20.dp),
        ),
        TextNode(BigText(label), textSizePx, textColor, maxLines = 1)
    )
)
```

---

### Ví dụ 3: Note row từ XML LinearLayout

XML gốc là một `LinearLayout` ngang full-width, icon 28dp bên trái, cột text
bên phải có `marginStart=16dp`, và dòng note có `marginTop=8dp`. Trong node DSL,
hai margin riêng lẻ này được biểu diễn bằng `SpaceNode`.

```kotlin
fun buildNoteRowFromXml(
    title: String,
    note: String,
    iconSource: BigImage,
): LayoutNode = LinearNode(
    orientation = Orientation.HORIZONTAL,
    crossAlign = CrossAlign.START,
    padding = EdgeInsets.symmetric(h = 16.dp, v = 8.dp),
    layoutWidth = LayoutDimension.MatchParent,
    layoutHeight = LayoutDimension.WrapContent,
    children = listOf(
        ImageNode(
            source = iconSource,
            layoutWidth = LayoutDimension.Fixed(28.dp),
            layoutHeight = LayoutDimension.Fixed(28.dp)
        ),
        SpaceNode.horizontal(16.dp),
        LinearNode(
            orientation = Orientation.VERTICAL,
            layoutWidth = LayoutDimension.MatchParent,
            layoutHeight = LayoutDimension.WrapContent,
            children = listOf(
                TextNode(
                    text = BigText(title),
                    textSizePx = 14.sp,
                    color = 0xFF202124.toInt(),
                    layoutWidth = LayoutDimension.MatchParent
                ),
                SpaceNode.vertical(8.dp),
                TextNode(
                    text = BigText(note),
                    textSizePx = 14.sp,
                    color = 0xFF5F6368.toInt(),
                    layoutWidth = LayoutDimension.MatchParent
                )
            )
        )
    )
)
```

`tools:text="Meet"` trong XML chỉ là dữ liệu preview, nên ở node nên truyền
text thật từ runtime qua `title` và `note`.

---

### Ví dụ 4: Bài học phức tạp (Linear lồng nhau)

```kotlin
fun buildLessonCard(
    wordBitmap: Bitmap,
    word: String,
    ipa: String,
    definition: String,
    textPrimary: Int,
    textSecondary: Int,
    textTertiary: Int,
): LinearNode = LinearNode(
    orientation = Orientation.HORIZONTAL,
    crossAlign  = CrossAlign.CENTER,
    gap         = 12.dp,
    padding     = EdgeInsets.symmetric(h = 16.dp, v = 14.dp),
    children    = listOf(
        // Icon bên trái
        ImageNode.fromBitmap(
            wordBitmap,
            layoutWidth = LayoutDimension.Fixed(36.dp),
            layoutHeight = LayoutDimension.Fixed(36.dp),
        ),
        // Cột text bên phải
        LinearNode(
            orientation = Orientation.VERTICAL,
            gap         = 2.dp,
            children    = listOf(
                TextNode(BigText(word),       16.sp, textPrimary,   maxLines = 1, typeface = Typeface.DEFAULT_BOLD),
                TextNode(BigText(ipa),        13.sp, textSecondary, maxLines = 1),
                TextNode(BigText(definition), 12.sp, textTertiary,  maxLines = 2),
            )
        )
    )
)
```

---

## 10. Mở rộng thêm node mới

Không cần sửa bất kỳ file nào của engine. Chỉ tạo 2 class:

### Bước 1 — Tạo `LayoutNode` con

```kotlin
/**
 * Node vẽ đường phân cách nằm ngang.
 */
data class DividerNode(
    val color     : Int,
    val thickness : Int = 1.dp,
    override val padding: EdgeInsets = EdgeInsets.ZERO
) : LayoutNode() {

    override fun measure(ctx: MeasureContext, c: Constraints, x: Int, y: Int): DrawSpec {
        val w = c.maxWidth
        val h = thickness + padding.vertical
        return DividerSpec(x, y, w, h, color, thickness, padding.top)
    }
}
```

### Bước 2 — Tạo `DrawSpec` con

```kotlin
class DividerSpec(
    override val left      : Int,
    override val top       : Int,
    override val width     : Int,
    override val height    : Int,
    private  val color     : Int,
    private  val thickness : Int,
    private  val offsetTop : Int,
) : DrawSpec() {

    private val paint = Paint().apply { this.color = this@DividerSpec.color }
    private val rect  = RectF()

    override fun onDrawContent(canvas: Canvas) {
        rect.set(0f, offsetTop.toFloat(), width.toFloat(), (offsetTop + thickness).toFloat())
        canvas.drawRect(rect, paint)
    }

    override fun withPosition(newLeft: Int, newTop: Int) =
        DividerSpec(newLeft, newTop, width, height, color, thickness, offsetTop)
}
```

### Bước 3 — Dùng ngay (không sửa gì thêm)

```kotlin
LinearNode(
    orientation = Orientation.VERTICAL,
    gap         = 0,
    children    = listOf(
        TextNode(BigText("Tiêu đề"),  18.sp, Color.BLACK, typeface = Typeface.DEFAULT_BOLD),
        DividerNode(color = 0xFFE5E7EB.toInt(), thickness = 1.dp),
        TextNode(BigText("Nội dung"), 14.sp, Color.DKGRAY),
    )
)
```

---

## 11. Hạn chế & khi nào KHÔNG nên dùng

| Tình huống | Giải pháp thay thế |
|-----------|-------------------|
| Width phụ thuộc parent động (ConstraintLayout co giãn) | XML View thông thường |
| Kích thước đổi liên tục theo animation | Compose / Custom View + `onDraw` trực tiếp |
| View nhỏ, đơn giản (1 text, 1 image) | `TextView` / `ImageView` thông thường — overhead bg còn đắt hơn |
| Cần `weight` / flex | Chưa hỗ trợ — cần tự thêm vào `LinearNode` |
| Cần wrap-line (text flow) | Chưa hỗ trợ — cần tạo `FlowNode` mới |
| Cần click / hit-test | Chưa hỗ trợ — cần thêm `id` vào node và walk spec tree |
| Cần background drawable | Chưa hỗ trợ — tạo `BoxNode` / `BackgroundSpec` |
| Cần border / loading outline | Dùng `OutlineNode` |
| `ConstraintNode` chain / Guideline / Barrier | Chưa hỗ trợ |

> ⚠️ **Lưu ý:** `PrecomputedView` báo kích thước `(0, 0)` khi `spec == null`. Tránh đặt view này trong `wrap_content` parent khi chưa có spec — layout có thể bị sụp đến 0 chiều cao.

---

## 12. Hướng phát triển tiếp theo

| Tính năng | Hướng làm |
|-----------|----------|
| `Weight` trong `LinearNode` | Thêm `weight: Float` vào `LayoutNode`, engine pass 2 chia phần dư theo weight. |
| Wrap-line (`FlowNode`) | Engine break khi `cursor + childWidth > maxWidth`, tạo hàng mới. |
| LRU Cache | `LruCache<Key, DrawSpec>` với `Key = hash(node, width, fontScale, density)`. |
| Hit-test (click) | Walk cây spec, check `(x, y)` rơi vào spec nào — gán `id` lên `LayoutNode` để map ngược. |
| Background | `LayoutNode.Box(child, background, radius)` → `BackgroundSpec(child)`. |
| `ConstraintNode` chain | Tính tổng size của chain → chia đều theo số member. |
| Pre-measure tại Repository | Đo ngay khi data về từ DB/network, cache vào memory → bind view ~0ms. |

---

## 13. Tham khảo nhanh

```kotlin
// ── Extension helper (định nghĩa trong project) ──────────────────────────────
val Int.dp: Int     get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.sp: Float get() = this * Resources.getSystem().displayMetrics.scaledDensity
val Int.sp: Float   get() = this.toFloat().sp

// ── Tạo node ─────────────────────────────────────────────────────────────────
val text  = TextNode(BigText("Hello"), 16.sp, Color.BLACK)
val image = ImageNode.fromBitmap(
    bmp,
    layoutWidth = LayoutDimension.Fixed(48.dp),
    layoutHeight = LayoutDimension.Fixed(48.dp)
)
val icon  = ImageNode(
    BigImage.ResSource(R.drawable.ic_star),
    layoutWidth = LayoutDimension.Fixed(24.dp),
    layoutHeight = LayoutDimension.Fixed(24.dp)
)
val row   = LinearNode(Orientation.HORIZONTAL, listOf(icon, text), gap = 8.dp,
                       crossAlign = CrossAlign.CENTER)
val space = SpaceNode.horizontal(12.dp)
val line = LineNode(
    color = 0xFFB8B8B8.toInt(),
    strokeWidth = 2.dp.toFloat(),
    dashWidth = 6.dp.toFloat(),
    dashGap = 6.dp.toFloat(),
    layoutWidth = LayoutDimension.Fixed(240.dp),
    layoutHeight = LayoutDimension.Fixed(2.dp)
)
val outline = OutlineNode(
    layoutWidth = LayoutDimension.MatchParent,
    layoutHeight = LayoutDimension.Fixed(72.dp),
    backgroundColor = 0x11E91E63,
    strokeColor = 0xFFE91E63.toInt(),
    strokeWidth = 2.dp.toFloat(),
    cornerRadius = 12.dp.toFloat(),
    state = OutlineState.LOADING
)
val fullWidthTitle = TextNode(
    BigText("Match parent"),
    18.sp,
    Color.BLACK,
    layoutWidth = LayoutDimension.MatchParent
)

// ── Đo (background thread) ───────────────────────────────────────────────────
val spec: DrawSpec = withContext(Dispatchers.Default) {
    LayoutEngine.measure(row, Constraints(containerWidthPx))
}

// ── Gán cho view (UI thread) ─────────────────────────────────────────────────
precomputedView.spec = spec       // auto requestLayout + invalidate

// ── ImageLoader (Application.onCreate) ──────────────────────────────────────
ImageLoader.install(GlideImageLoader(this))

// ── EdgeInsets shortcuts ─────────────────────────────────────────────────────
EdgeInsets.ZERO                         // không padding
EdgeInsets.all(16.dp)                   // mọi phía
EdgeInsets.symmetric(h = 16.dp, v = 8.dp)

// ── LayoutDimension ──────────────────────────────────────────────────────────
LayoutDimension.WrapContent             // giống wrap_content
LayoutDimension.MatchParent             // giống match_parent khi constraint hữu hạn
LayoutDimension.Fixed(64.dp)            // size cố định px

// ── CrossAlign ────────────────────────────────────────────────────────────────
CrossAlign.START   // căn đầu (top hoặc left)
CrossAlign.CENTER  // căn giữa
CrossAlign.END     // căn cuối (bottom hoặc right)
```

---

*Tài liệu này mô tả trạng thái hiện tại của `node-engine`. Các tính năng chưa có (weight, hit-test, background...) được ghi rõ trong [Mục 12](#12-hướng-phát-triển-tiếp-theo).*
