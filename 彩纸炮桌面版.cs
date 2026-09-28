using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Media;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;

namespace DesktopConfettiCannon
{
    public static class Program
    {
        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [STAThread]
        public static void Main(string[] args)
        {
            try { SetProcessDPIAware(); }
            catch { }

            bool demo = false;
            for (int i = 0; i < args.Length; i++)
            {
                if (string.Equals(args[i], "--demo", StringComparison.OrdinalIgnoreCase)) demo = true;
            }

            Application app = new Application();
            app.ShutdownMode = ShutdownMode.OnExplicitShutdown;

            OverlayWindow overlay = new OverlayWindow(demo);
            LauncherWindow launcher = new LauncherWindow(overlay);

            overlay.Show();
            launcher.Show();
            app.Run();
        }
    }

    public sealed class OverlayWindow : Window
    {
        private const double GRAVITY = 1250.0;
        private const double BARREL = 95.0;
        private const double AIM_ANGLE = -0.55; // 向上 31 度左右

        private const int WM_HOTKEY = 0x0312;
        private const int MOD_CONTROL = 0x0002;
        private const int MOD_ALT = 0x0001;
        private const int MOD_NOREPEAT = 0x4000;
        private const int HOTKEY_FIRE = 1;
        private const int HOTKEY_AUTO = 2;

        private const long WS_EX_TRANSPARENT = 0x00000020;
        private const long WS_EX_LAYERED = 0x00080000;
        private const long WS_EX_TOOLWINDOW = 0x00000080;
        private const long WS_EX_NOACTIVATE = 0x08000000;

        private readonly List<Confetto> confetti = new List<Confetto>();
        private readonly List<Flash> flashes = new List<Flash>();
        private readonly List<Smoke> smokes = new List<Smoke>();
        private readonly SolidColorBrush[] colorBrushes;
        private readonly Random rand = new Random();
        private readonly Stopwatch clock = new Stopwatch();
        private readonly bool demoMode;

        private CanvasHost host;
        private double groundY;
        private double leftCannonX;
        private double rightCannonX;
        private double cannonY;
        private double[] recoil = new double[2];
        private double shake;
        private double totalTime;
        private double lastTime;
        private double nextAutoTime;
        private bool soundEnabled = true;
        private bool autoFire;
        private int nextSide;
        private IntPtr hwnd;

        private readonly Brush red = CreateBrush(0xFFE11D48);
        private readonly Brush redDark = CreateBrush(0xFF7A1030);
        private readonly Brush gold = CreateBrush(0xFFF2A33C);
        private readonly Brush goldDark = CreateBrush(0xFFB85F22);
        private readonly Brush wood = CreateBrush(0xFF8A5A2B);
        private readonly Brush woodDark = CreateBrush(0xFF5F3A1A);
        private readonly Brush wheelOuter = CreateBrush(0xFF3C3C52);
        private readonly Brush wheelInner = CreateBrush(0xFFD9D2B8);
        private readonly Brush whiteLow = CreateBrush(0x33FFFFFF);
        private readonly Brush whiteVeryLow = CreateBrush(0x12FFFFFF);
        private readonly Brush smokeBrush = CreateBrush(0xFFFFFFFF);
        private readonly Pen legPen;
        private StreamGeometry coneGeo;

        public OverlayWindow(bool demo)
        {
            demoMode = demo;
            clock.Start();

            WindowStyle = WindowStyle.None;
            AllowsTransparency = true;
            Background = Brushes.Transparent;
            Topmost = true;
            ShowInTaskbar = false;
            Focusable = false;
            ShowActivated = false;
            ResizeMode = ResizeMode.NoResize;

            double left = SystemParameters.VirtualScreenLeft;
            double top = SystemParameters.VirtualScreenTop;
            double width = SystemParameters.VirtualScreenWidth;
            double height = SystemParameters.VirtualScreenHeight;
            if (width <= 0 || height <= 0)
            {
                left = 0; top = 0;
                width = SystemParameters.PrimaryScreenWidth;
                height = SystemParameters.PrimaryScreenHeight;
            }
            Left = left;
            Top = top;
            Width = width;
            Height = height;

            colorBrushes = CreateColorBrushes();
            legPen = new Pen(wood, 9);
            legPen.StartLineCap = PenLineCap.Round;
            legPen.EndLineCap = PenLineCap.Round;
            BuildConeGeometry();

            groundY = top + height - 4;
            leftCannonX = left + 170;
            rightCannonX = left + width - 170;
            cannonY = groundY - 92;

            SourceInitialized += OnSourceInitialized;
            Loaded += OnLoaded;
            Closed += OnClosed;

            CompositionTarget.Rendering += OnRendering;
        }

        public bool SoundEnabled
        {
            get { return soundEnabled; }
            set { soundEnabled = value; }
        }

        public bool AutoFire
        {
            get { return autoFire; }
            set
            {
                autoFire = value;
                if (autoFire) nextAutoTime = totalTime + 0.35;
            }
        }

        private static Brush CreateBrush(uint argb)
        {
            SolidColorBrush b = new SolidColorBrush(Color.FromArgb(
                (byte)(argb >> 24), (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb));
            b.Freeze();
            return b;
        }

        private static SolidColorBrush[] CreateColorBrushes()
        {
            uint[] cols = new uint[]
            {
                0xFFFF5D8F, 0xFFFFD166, 0xFF06D6A0, 0xFF118AB2,
                0xFF8338EC, 0xFFFB5607, 0xFF3A86FF, 0xFFEF476F,
                0xFFFFBE0B, 0xFFFFFFFF, 0xFFFF9F1C, 0xFF9BF6FF
            };
            SolidColorBrush[] arr = new SolidColorBrush[cols.Length];
            for (int i = 0; i < cols.Length; i++) arr[i] = (SolidColorBrush)CreateBrush(cols[i]);
            return arr;
        }

        private void BuildConeGeometry()
        {
            coneGeo = new StreamGeometry();
            using (StreamGeometryContext gc = coneGeo.Open())
            {
                gc.BeginFigure(new Point(52, -14), true, true);
                gc.LineTo(new Point(52, 14), true, false);
                gc.LineTo(new Point(96, 0), true, false);
            }
            coneGeo.Freeze();
        }

        private void OnSourceInitialized(object sender, EventArgs e)
        {
            hwnd = new WindowInteropHelper(this).Handle;
            HwndSource src = HwndSource.FromHwnd(hwnd);
            if (src != null) src.AddHook(WndProc);

            // 让全屏透明层不挡鼠标、不抢焦点
            long ex = GetWindowLongPtr(hwnd, GWL_EXSTYLE).ToInt64();
            ex |= WS_EX_TRANSPARENT | WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
            SetWindowLongPtr(hwnd, GWL_EXSTYLE, new IntPtr(ex));

            RegisterHotKey(hwnd, HOTKEY_FIRE, MOD_CONTROL | MOD_ALT | MOD_NOREPEAT, 0x4B); // Ctrl+Alt+K
            RegisterHotKey(hwnd, HOTKEY_AUTO, MOD_CONTROL | MOD_ALT | MOD_NOREPEAT, 0x4C); // Ctrl+Alt+L
        }

        private IntPtr WndProc(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
        {
            if (msg == WM_HOTKEY)
            {
                int id = wParam.ToInt32();
                if (id == HOTKEY_FIRE)
                {
                    Fire();
                    handled = true;
                }
                else if (id == HOTKEY_AUTO)
                {
                    AutoFire = !AutoFire;
                    handled = true;
                }
            }
            return IntPtr.Zero;
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            host = new CanvasHost();
            Content = host;
            host.Render(delegate { });

            if (demoMode)
            {
                SoundEnabled = false;
                DispatcherTimer burst = new DispatcherTimer();
                burst.Interval = TimeSpan.FromMilliseconds(520);
                int[] count = new int[1];
                burst.Tick += delegate
                {
                    count[0]++;
                    Fire();
                    if (count[0] >= 6) burst.Stop();
                };
                burst.Start();

                DispatcherTimer exit = new DispatcherTimer();
                exit.Interval = TimeSpan.FromSeconds(6.8);
                exit.Tick += delegate { Application.Current.Shutdown(); };
                exit.Start();

                DispatcherTimer first = new DispatcherTimer();
                first.Interval = TimeSpan.FromMilliseconds(150);
                first.Tick += delegate
                {
                    Fire();
                    first.Stop();
                };
                first.Start();
            }
        }

        private void OnClosed(object sender, EventArgs e)
        {
            CompositionTarget.Rendering -= OnRendering;
            if (hwnd != IntPtr.Zero)
            {
                UnregisterHotKey(hwnd, HOTKEY_FIRE);
                UnregisterHotKey(hwnd, HOTKEY_AUTO);
            }
        }

        public void Fire()
        {
            int side = nextSide;
            nextSide = 1 - nextSide;

            double px = (side == 0) ? leftCannonX : rightCannonX;
            bool mirror = (side == 1);
            double dirX = Math.Cos(AIM_ANGLE) * (mirror ? -1.0 : 1.0);
            double dirY = Math.Sin(AIM_ANGLE);
            double mx = px + dirX * (BARREL - recoil[side] * 12);
            double my = cannonY + dirY * (BARREL - recoil[side] * 12);

            double areaScale = Math.Min(1.0, (Width * Height) / (1440.0 * 850.0));
            int count = (int)(70 + rand.NextDouble() * 70 + 70 * areaScale);

            for (int i = 0; i < count; i++)
            {
                double a = AIM_ANGLE + (rand.NextDouble() - 0.5) * 0.62;
                double speed = 760 + rand.NextDouble() * 700;
                double vx = Math.Cos(a) * speed * (mirror ? -1.0 : 1.0);
                double vy = Math.Sin(a) * speed;

                Confetto c = new Confetto();
                c.X = mx + (rand.NextDouble() - 0.5) * 16;
                c.Y = my + (rand.NextDouble() - 0.5) * 16;
                c.VX = vx;
                c.VY = vy;
                c.Rot = rand.NextDouble() * Math.PI * 2;
                c.VR = (rand.NextDouble() - 0.5) * 13;
                c.Life = 2.4 + rand.NextDouble() * 2.0;
                c.MaxLife = c.Life;
                c.Seed = rand.NextDouble() * 100;
                c.ColorIndex = rand.Next(colorBrushes.Length);

                double roll = rand.NextDouble();
                if (roll < 0.42)
                {
                    c.Type = 0; // 方块
                    c.W = 7 + rand.NextDouble() * 9;
                    c.H = 4 + rand.NextDouble() * 3;
                }
                else if (roll < 0.72)
                {
                    c.Type = 1; // 彩带
                    c.W = 16 + rand.NextDouble() * 12;
                    c.H = 3.5 + rand.NextDouble() * 2.5;
                    c.VR = c.VR * 1.8;
                }
                else if (roll < 0.90)
                {
                    c.Type = 2; // 圆点
                    c.W = 3.5 + rand.NextDouble() * 4.5;
                }
                else
                {
                    c.Type = 3; // 金色闪光
                    c.ColorIndex = (c.ColorIndex % 4 == 0) ? 1 : 9;
                    c.W = 7 + rand.NextDouble() * 6;
                    c.VR = c.VR * 2.4;
                }
                confetti.Add(c);
            }

            flashes.Add(new Flash(mx, my));
            for (int i = 0; i < 6; i++)
            {
                double a = AIM_ANGLE + (rand.NextDouble() - 0.5) * 0.8;
                smokes.Add(new Smoke(
                    mx + (rand.NextDouble() - 0.5) * 12,
                    my + (rand.NextDouble() - 0.5) * 12,
                    Math.Cos(a) * (60 + rand.NextDouble() * 90) * (mirror ? -1.0 : 1.0),
                    Math.Sin(a) * (60 + rand.NextDouble() * 90) - 40,
                    6 + rand.NextDouble() * 8));
            }

            recoil[side] = 1.0;
            shake = Math.Min(14, 7 + count / 30.0);
            if (SoundEnabled) PopSound.Play();

            if (confetti.Count > 4200) confetti.RemoveRange(0, confetti.Count - 4200);
            if (smokes.Count > 500) smokes.RemoveRange(0, smokes.Count - 500);
        }

        private void OnRendering(object sender, EventArgs e)
        {
            double now = clock.Elapsed.TotalSeconds;
            double dt = Math.Min(0.05, now - lastTime);
            lastTime = now;
            totalTime += dt;

            Update(dt);
            if (host != null)
            {
                host.Render(delegate(DrawingContext dc) { Draw(dc); });
            }
        }

        private void Update(double dt)
        {
            shake = Math.Max(0, shake - 32 * dt);
            for (int i = 0; i < recoil.Length; i++)
            {
                recoil[i] = Math.Max(0, recoil[i] - 4.5 * dt);
            }

            for (int i = confetti.Count - 1; i >= 0; i--)
            {
                Confetto c = confetti[i];
                c.Life -= dt;
                if (c.Life <= 0)
                {
                    confetti.RemoveAt(i);
                    continue;
                }

                c.VY += GRAVITY * dt;
                c.VX *= Math.Max(0, 1 - 0.18 * dt);
                c.X += c.VX * dt;
                c.Y += c.VY * dt;
                c.Rot += (c.VR + Math.Sin(totalTime * 9 + c.Seed) * 2.0) * dt;
                c.VR *= Math.Max(0, 1 - 1.6 * dt);

                if (c.Y > groundY)
                {
                    if (c.Bounces < 2 && Math.Abs(c.VY) > 240)
                    {
                        c.Y = groundY;
                        c.VY *= -0.34;
                        c.VX *= 0.62;
                        c.VR *= 0.45;
                        c.Bounces++;
                    }
                    else
                    {
                        c.Y = groundY;
                        c.VY = Math.Min(0, c.VY * 0.15);
                        c.VX *= Math.Max(0, 1 - 2.6 * dt);
                    }
                }
            }

            for (int i = flashes.Count - 1; i >= 0; i--)
            {
                flashes[i].Life -= dt;
                if (flashes[i].Life <= 0) flashes.RemoveAt(i);
            }

            for (int i = smokes.Count - 1; i >= 0; i--)
            {
                Smoke s = smokes[i];
                s.Life -= dt;
                if (s.Life <= 0)
                {
                    smokes.RemoveAt(i);
                    continue;
                }
                s.X += s.VX * dt;
                s.Y += s.VY * dt;
                s.R += 16 * dt;
            }

            if (autoFire && totalTime >= nextAutoTime)
            {
                nextAutoTime = totalTime + 0.72;
                Fire();
            }
        }

        private void Draw(DrawingContext dc)
        {
            if (shake > 0.25)
            {
                dc.PushTransform(new TranslateTransform(
                    (rand.NextDouble() - 0.5) * shake,
                    (rand.NextDouble() - 0.5) * shake));
            }

            DrawSmoke(dc);
            DrawCannon(dc, leftCannonX, cannonY, false, recoil[0]);
            DrawCannon(dc, rightCannonX, cannonY, true, recoil[1]);
            DrawFlashes(dc);
            DrawConfetti(dc);

            if (shake > 0.25) dc.Pop();
        }

        private void DrawConfetti(DrawingContext dc)
        {
            for (int i = 0; i < confetti.Count; i++)
            {
                Confetto c = confetti[i];
                double alpha = (c.Life < 0.45) ? Math.Max(0, c.Life / 0.45) : 1.0;
                if (alpha <= 0.001) continue;

                SolidColorBrush b = (SolidColorBrush)colorBrushes[c.ColorIndex];
                dc.PushOpacity(alpha);
                dc.PushTransform(new TranslateTransform(c.X, c.Y));

                if (c.Type == 2)
                {
                    dc.DrawEllipse(b, null, new Point(0, 0), c.W, c.W);
                }
                else
                {
                    dc.PushTransform(new RotateTransform(c.Rot * 180.0 / Math.PI));
                    if (c.Type == 3)
                    {
                        // 四角闪光：横竖两道
                        dc.DrawRectangle(b, null, new Rect(-c.W, -c.W * 0.16, c.W * 2, c.W * 0.32));
                        dc.DrawRectangle(b, null, new Rect(-c.W * 0.16, -c.W, c.W * 0.32, c.W * 2));
                    }
                    else
                    {
                        dc.DrawRectangle(b, null, new Rect(-c.W / 2, -c.H / 2, c.W, c.H));
                    }
                    dc.Pop();
                }

                dc.Pop();
                dc.Pop();
            }
        }

        private void DrawSmoke(DrawingContext dc)
        {
            for (int i = 0; i < smokes.Count; i++)
            {
                Smoke s = smokes[i];
                double k = 1.0 - s.Life / s.MaxLife;
                dc.PushOpacity(0.14 * (1.0 - k));
                dc.DrawEllipse(smokeBrush, null, new Point(s.X, s.Y), s.R, s.R);
                dc.Pop();
            }
        }

        private void DrawFlashes(DrawingContext dc)
        {
            for (int i = 0; i < flashes.Count; i++)
            {
                Flash f = flashes[i];
                double k = 1.0 - f.Life / f.MaxLife;
                double r = f.BaseR * (1.0 + k * 1.8);

                RadialGradientBrush g = new RadialGradientBrush();
                g.GradientStops.Add(new GradientStop(Color.FromArgb((byte)(245 * (1.0 - k)), 255, 246, 190), 0.0));
                g.GradientStops.Add(new GradientStop(Color.FromArgb((byte)(150 * (1.0 - k)), 255, 190, 80), 0.45));
                g.GradientStops.Add(new GradientStop(Color.FromArgb(0, 255, 140, 40), 1.0));
                g.Freeze();
                dc.DrawEllipse(g, null, new Point(f.X, f.Y), r, r);
            }
        }

        private void DrawCannon(DrawingContext dc, double px, double py, bool mirror, double rec)
        {
            // 阴影
            dc.PushOpacity(0.28);
            dc.DrawEllipse(redDark, null, new Point(px, groundY - 2), 58, 7);
            dc.Pop();

            // 底座
            dc.DrawLine(legPen, new Point(px - 16, py + 14), new Point(px - 32, groundY - 6));
            dc.DrawLine(legPen, new Point(px + 16, py + 14), new Point(px + 34, groundY - 6));
            dc.DrawRectangle(woodDark, null, new Rect(px - 42, groundY - 12, 84, 9));

            // 轮子
            DrawWheel(dc, px - 32, groundY - 20);
            DrawWheel(dc, px + 34, groundY - 20);

            // 炮管
            dc.PushTransform(new TranslateTransform(px, py));
            if (mirror) dc.PushTransform(new ScaleTransform(-1, 1));
            dc.PushTransform(new RotateTransform(AIM_ANGLE * 180.0 / Math.PI));
            dc.PushTransform(new TranslateTransform(-rec * 12, 0));

            // 尾盖
            dc.DrawEllipse(redDark, null, new Point(-46, 0), 6, 14);
            // 炮身
            dc.DrawRoundedRectangle(red, null, new Rect(-44, -15, 98, 30), 9, 9);
            // 白色装饰条
            for (int x = -28; x < 52; x += 17)
            {
                dc.DrawRoundedRectangle(whiteLow, null, new Rect(x - 3, -15, 6, 30), 2, 2);
            }
            // 高光
            dc.DrawRoundedRectangle(whiteVeryLow, null, new Rect(-32, -10, 74, 5), 2, 2);
            // 金色锥形炮口
            dc.DrawGeometry(gold, null, coneGeo);
            dc.DrawEllipse(goldDark, null, new Point(97, 0), 5, 13);
            // 炮口微光
            dc.PushOpacity(0.12);
            dc.DrawEllipse(Brushes.White, null, new Point(98, 0), 6, 14);
            dc.Pop();

            dc.Pop(); // recoil
            dc.Pop(); // rotate
            if (mirror) dc.Pop(); // scale
            dc.Pop(); // translate
        }

        private void DrawWheel(DrawingContext dc, double x, double y)
        {
            dc.DrawEllipse(wheelOuter, null, new Point(x, y), 13, 13);
            dc.DrawEllipse(wheelInner, null, new Point(x, y), 6, 6);
        }

        [DllImport("user32.dll")]
        private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

        [DllImport("user32.dll")]
        private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

        private const int GWL_EXSTYLE = -20;

        [DllImport("user32.dll", EntryPoint = "GetWindowLongPtr")]
        private static extern IntPtr GetWindowLongPtr64(IntPtr hWnd, int nIndex);

        [DllImport("user32.dll", EntryPoint = "GetWindowLong")]
        private static extern IntPtr GetWindowLong32(IntPtr hWnd, int nIndex);

        [DllImport("user32.dll", EntryPoint = "SetWindowLongPtr")]
        private static extern IntPtr SetWindowLongPtr64(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

        [DllImport("user32.dll", EntryPoint = "SetWindowLong")]
        private static extern IntPtr SetWindowLong32(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

        private static IntPtr GetWindowLongPtr(IntPtr hWnd, int nIndex)
        {
            if (IntPtr.Size == 8) return GetWindowLongPtr64(hWnd, nIndex);
            return GetWindowLong32(hWnd, nIndex);
        }

        private static void SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr value)
        {
            if (IntPtr.Size == 8) SetWindowLongPtr64(hWnd, nIndex, value);
            else SetWindowLong32(hWnd, nIndex, value);
        }
    }

    public sealed class LauncherWindow : Window
    {
        private readonly OverlayWindow overlay;
        private readonly Button fireButton;
        private readonly Button autoButton;
        private readonly Button soundButton;

        public LauncherWindow(OverlayWindow ov)
        {
            overlay = ov;
            WindowStyle = WindowStyle.None;
            AllowsTransparency = true;
            Background = Brushes.Transparent;
            Topmost = true;
            ShowInTaskbar = false;
            ResizeMode = ResizeMode.NoResize;
            SizeToContent = SizeToContent.WidthAndHeight;
            FontFamily = new FontFamily("Microsoft YaHei");

            Border root = new Border();
            root.Background = (Brush)CreateBrush(0xE61B1E3C);
            root.BorderBrush = (Brush)CreateBrush(0x66FFFFFF);
            root.BorderThickness = new Thickness(1);
            root.CornerRadius = new CornerRadius(12);
            root.Padding = new Thickness(14, 10, 14, 10);
            root.MouseLeftButtonDown += OnRootMouseDown;

            StackPanel panel = new StackPanel();
            root.Child = panel;

            TextBlock title = new TextBlock();
            title.Text = "桌面彩纸炮";
            title.FontSize = 15;
            title.FontWeight = FontWeights.Bold;
            title.Foreground = Brushes.White;
            title.HorizontalAlignment = HorizontalAlignment.Center;
            title.Margin = new Thickness(0, 0, 0, 8);
            panel.Children.Add(title);

            Grid grid = new Grid();
            grid.Margin = new Thickness(0, 0, 0, 6);
            ColumnDefinition col1 = new ColumnDefinition();
            col1.Width = new GridLength(1, GridUnitType.Star);
            ColumnDefinition col2 = new ColumnDefinition();
            col2.Width = new GridLength(1, GridUnitType.Star);
            RowDefinition row1 = new RowDefinition();
            row1.Height = GridLength.Auto;
            RowDefinition row2 = new RowDefinition();
            row2.Height = GridLength.Auto;
            grid.ColumnDefinitions.Add(col1);
            grid.ColumnDefinitions.Add(col2);
            grid.RowDefinitions.Add(row1);
            grid.RowDefinitions.Add(row2);

            fireButton = MakeButton("发射");
            fireButton.Background = (Brush)CreateBrush(0xFFE11D48);
            fireButton.BorderBrush = (Brush)CreateBrush(0xAAFFFFFF);
            fireButton.Click += delegate { overlay.Fire(); };
            Grid.SetRow(fireButton, 0);
            Grid.SetColumn(fireButton, 0);
            grid.Children.Add(fireButton);

            autoButton = MakeButton("连发：关");
            autoButton.Click += delegate
            {
                overlay.AutoFire = !overlay.AutoFire;
                autoButton.Content = overlay.AutoFire ? "连发：开" : "连发：关";
            };
            Grid.SetRow(autoButton, 0);
            Grid.SetColumn(autoButton, 1);
            grid.Children.Add(autoButton);

            soundButton = MakeButton("音效：开");
            soundButton.Click += delegate
            {
                overlay.SoundEnabled = !overlay.SoundEnabled;
                soundButton.Content = overlay.SoundEnabled ? "音效：开" : "音效：关";
            };
            Grid.SetRow(soundButton, 1);
            Grid.SetColumn(soundButton, 0);
            grid.Children.Add(soundButton);

            Button quitButton = MakeButton("退出");
            quitButton.Click += delegate { Application.Current.Shutdown(); };
            Grid.SetRow(quitButton, 1);
            Grid.SetColumn(quitButton, 1);
            grid.Children.Add(quitButton);

            panel.Children.Add(grid);

            TextBlock hint = new TextBlock();
            hint.Text = "Ctrl+Alt+K 发射   ·   Ctrl+Alt+L 连发";
            hint.FontSize = 11;
            hint.Foreground = (Brush)CreateBrush(0xB8FFFFFF);
            hint.HorizontalAlignment = HorizontalAlignment.Center;
            panel.Children.Add(hint);

            Content = root;
            Loaded += OnLoaded;
        }

        private static Brush CreateBrush(uint argb)
        {
            SolidColorBrush b = new SolidColorBrush(Color.FromArgb(
                (byte)(argb >> 24), (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb));
            b.Freeze();
            return b;
        }

        private Button MakeButton(string text)
        {
            Button b = new Button();
            b.Content = text;
            b.FontSize = 13;
            b.Foreground = Brushes.White;
            b.Background = (Brush)CreateBrush(0x4DFFFFFF);
            b.BorderBrush = (Brush)CreateBrush(0x55FFFFFF);
            b.BorderThickness = new Thickness(1);
            b.Padding = new Thickness(12, 6, 12, 6);
            b.Margin = new Thickness(3);
            b.Cursor = Cursors.Hand;
            b.HorizontalAlignment = HorizontalAlignment.Stretch;
            return b;
        }

        private void OnRootMouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ButtonState == MouseButtonState.Pressed && e.ChangedButton == MouseButton.Left)
            {
                try { DragMove(); }
                catch { }
            }
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            Rect wa = SystemParameters.WorkArea;
            Left = wa.Right - ActualWidth - 18;
            Top = wa.Bottom - ActualHeight - 18;
        }
    }

    public sealed class CanvasHost : FrameworkElement
    {
        private readonly DrawingVisual visual = new DrawingVisual();

        public CanvasHost()
        {
            AddVisualChild(visual);
        }

        protected override int VisualChildrenCount
        {
            get { return 1; }
        }

        protected override Visual GetVisualChild(int index)
        {
            return visual;
        }

        public void Render(Action<DrawingContext> draw)
        {
            using (DrawingContext dc = visual.RenderOpen())
            {
                draw(dc);
            }
        }
    }

    public sealed class Confetto
    {
        public double X;
        public double Y;
        public double VX;
        public double VY;
        public double Rot;
        public double VR;
        public double W;
        public double H;
        public double Life;
        public double MaxLife;
        public double Seed;
        public int Type;
        public int ColorIndex;
        public int Bounces;
    }

    public sealed class Flash
    {
        public double X;
        public double Y;
        public double Life;
        public double MaxLife;
        public double BaseR;

        public Flash(double x, double y)
        {
            X = x;
            Y = y;
            Life = 0.16;
            MaxLife = 0.16;
            BaseR = 30 + new Random().NextDouble() * 12;
        }
    }

    public sealed class Smoke
    {
        public double X;
        public double Y;
        public double VX;
        public double VY;
        public double R;
        public double Life;
        public double MaxLife;

        public Smoke(double x, double y, double vx, double vy, double r)
        {
            X = x;
            Y = y;
            VX = vx;
            VY = vy;
            R = r;
            Life = 0.55;
            MaxLife = 0.55;
        }
    }

    public static class PopSound
    {
        private static SoundPlayer player;

        public static void Play()
        {
            try
            {
                if (player == null)
                {
                    string path = Path.Combine(Path.GetTempPath(), "confetti-pop.wav");
                    if (!File.Exists(path)) File.WriteAllBytes(path, BuildWav());
                    player = new SoundPlayer(path);
                    player.Load();
                }
                player.Play();
            }
            catch
            {
                // 音频失败不影响彩纸
            }
        }

        private static byte[] BuildWav()
        {
            int rate = 22050;
            double duration = 0.18;
            int n = (int)(rate * duration);
            short[] data = new short[n];
            Random rnd = new Random(2026);

            for (int i = 0; i < n; i++)
            {
                double t = (double)i / rate;
                double noise = (rnd.NextDouble() * 2 - 1) * Math.Exp(-t * 28.0);
                double thump = Math.Sin(2 * Math.PI * 150 * t) * Math.Exp(-t * 24.0) * 0.5;
                double crack = Math.Sin(2 * Math.PI * 620 * t) * Math.Exp(-t * 110.0) * 0.35;
                double v = (noise * 0.8 + thump + crack) * 0.72;
                if (v > 1) v = 1;
                if (v < -1) v = -1;
                data[i] = (short)(v * 32767);
            }

            using (MemoryStream ms = new MemoryStream())
            using (BinaryWriter w = new BinaryWriter(ms))
            {
                int dataBytes = n * 2;
                w.Write(0x46464952);          // "RIFF"
                w.Write(36 + dataBytes);
                w.Write(0x45564157);          // "WAVE"
                w.Write(0x20746D66);          // "fmt "
                w.Write(16);
                w.Write((short)1);
                w.Write((short)1);
                w.Write(rate);
                w.Write(rate * 2);
                w.Write((short)2);
                w.Write((short)16);
                w.Write(0x61746164);          // "data"
                w.Write(dataBytes);
                for (int i = 0; i < n; i++) w.Write(data[i]);
                w.Flush();
                return ms.ToArray();
            }
        }
    }
}
