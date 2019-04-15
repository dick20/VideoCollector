# VideoCollector
视频采集器（收集手机传感器信息，摄像头的焦距等）



## 修改的地方

CollectDataActivity类

+ 使用的是Camera（可以改进为Camera2
+ 通过拖动seekbar调整焦距
+ 保存数据到内部存储卡

用到的工具类

- CommonUtil 获取时间戳
- SensorUtile 获取传感器信息
  - 方向
  - 磁场
  - 加速度