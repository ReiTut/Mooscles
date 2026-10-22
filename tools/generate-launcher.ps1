param([string]$SourcePath = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SourcePath) { $SourcePath = Join-Path $projectRoot 'artwork\arm-dumbbell-source.jpg' }
Add-Type -AssemblyName System.Drawing
$drawingReferences = @('System.Drawing.Common', 'System.Drawing.Primitives', 'System.Collections', 'System.Runtime', 'System.Runtime.InteropServices') + @(Get-ChildItem -LiteralPath $PSHOME -Filter 'System.Private.Windows.*.dll' -File | Select-Object -ExpandProperty FullName)
Add-Type -ReferencedAssemblies $drawingReferences -TypeDefinition @'
using System;
using System.IO;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Drawing.Drawing2D;

public static class MoosclesLauncher {
    static readonly Color Background = Color.FromArgb(16, 23, 19);
    static bool IsBackdrop(Color c) {
        int high = Math.Max(c.R, Math.Max(c.G, c.B));
        int low = Math.Min(c.R, Math.Min(c.G, c.B));
        return low >= 165 && high - low <= 32;
    }
    static Graphics Configure(Bitmap bitmap) {
        Graphics g = Graphics.FromImage(bitmap);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        g.CompositingQuality = CompositingQuality.HighQuality;
        return g;
    }
    static GraphicsPath Rounded(RectangleF r, float radius) {
        var p = new GraphicsPath(); float d = radius * 2;
        p.AddArc(r.Left, r.Top, d, d, 180, 90); p.AddArc(r.Right-d, r.Top, d, d, 270, 90);
        p.AddArc(r.Right-d, r.Bottom-d, d, d, 0, 90); p.AddArc(r.Left, r.Bottom-d, d, d, 90, 90);
        p.CloseFigure(); return p;
    }
    static Bitmap MaskedIcon(Bitmap foreground, int size, bool round, bool monochrome) {
        var image = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Configure(image)) {
            using (var shape = round ? new GraphicsPath() : Rounded(new RectangleF(0,0,size,size), size*.23f)) {
                if (round) shape.AddEllipse(0,0,size,size);
                g.SetClip(shape); g.Clear(monochrome ? Color.FromArgb(131,219,176) : Background);
                // Adaptive launchers expose the middle 72dp of their 108dp layers.
                float edge = size * 1.5f;
                g.DrawImage(foreground, new RectangleF((size-edge)/2,(size-edge)/2,edge,edge));
            }
        }
        return image;
    }
    public static string Generate(string source, string root) {
        string res = Path.Combine(root,"app","src","main","res");
        string art = Path.Combine(root,"artwork"); Directory.CreateDirectory(art);
        string previews = Path.Combine(root,"app","build","outputs","icon-previews"); Directory.CreateDirectory(previews);
        using (var input = new Bitmap(source)) {
            int w=input.Width, h=input.Height;
            // Only edge-connected neutral checkerboard is removed. Original foreground RGB is unchanged.
            bool[,] outside = new bool[w,h]; var pending = new Queue<Point>();
            Action<int,int> visit = (x,y) => { if(x>=0 && x<w && y>=0 && y<h && !outside[x,y] && IsBackdrop(input.GetPixel(x,y))) { outside[x,y]=true; pending.Enqueue(new Point(x,y)); } };
            for(int x=0;x<w;x++){visit(x,0);visit(x,h-1);} for(int y=0;y<h;y++){visit(0,y);visit(w-1,y);}
            while(pending.Count>0){var p=pending.Dequeue();visit(p.X-1,p.Y);visit(p.X+1,p.Y);visit(p.X,p.Y-1);visit(p.X,p.Y+1);}
            using(var cutout=new Bitmap(w,h,PixelFormat.Format32bppArgb)) {
                int left=w, top=h, right=0, bottom=0, retained=0;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(!outside[x,y]) {
                    var pixel=input.GetPixel(x,y);
                    // Interior dumbbell highlights retain their original RGB and opacity.
                    cutout.SetPixel(x,y,Color.FromArgb(255,pixel.R,pixel.G,pixel.B)); retained++;
                    left=Math.Min(left,x);top=Math.Min(top,y);right=Math.Max(right,x);bottom=Math.Max(bottom,y);
                }
                if(retained < 10000 || left==0 || top==0 || right==w-1 || bottom==h-1) throw new Exception("Artwork extraction bounds are invalid");
                cutout.Save(Path.Combine(art,"arm-dumbbell-foreground.png"),ImageFormat.Png);
                double cx=(left+right)/2.0, cy=(top+bottom)/2.0, radius=1;
                for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)if(cutout.GetPixel(x,y).A>0)radius=Math.Max(radius,Math.Sqrt((x-cx)*(x-cx)+(y-cy)*(y-cy)));
                double scale=32.0*4/radius;
                using(var foreground=new Bitmap(432,432,PixelFormat.Format32bppArgb)) {
                    using(var g=Configure(foreground))g.DrawImage(cutout,new RectangleF((float)(216-cx*scale),(float)(216-cy*scale),(float)(w*scale),(float)(h*scale)));
                    string drawable=Path.Combine(res,"drawable-nodpi");Directory.CreateDirectory(drawable);
                    foreground.Save(Path.Combine(drawable,"ic_launcher_foreground.png"),ImageFormat.Png);
                    using(var mono=new Bitmap(432,432,PixelFormat.Format32bppArgb)) {
                        for(int y=0;y<432;y++)for(int x=0;x<432;x++)mono.SetPixel(x,y,Color.FromArgb(foreground.GetPixel(x,y).A,255,255,255));
                        mono.Save(Path.Combine(drawable,"ic_launcher_monochrome.png"),ImageFormat.Png);
                        var densities=new string[]{"mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"};var sizes=new int[]{48,72,96,144,192};
                        for(int i=0;i<sizes.Length;i++) {
                            string folder=Path.Combine(res,"mipmap-"+densities[i]);Directory.CreateDirectory(folder);
                            using(var square=MaskedIcon(foreground,sizes[i],false,false))square.Save(Path.Combine(folder,"ic_launcher.png"),ImageFormat.Png);
                            using(var circle=MaskedIcon(foreground,sizes[i],true,false))circle.Save(Path.Combine(folder,"ic_launcher_round.png"),ImageFormat.Png);
                        }
                        using(var preview=new Bitmap(1536,620,PixelFormat.Format32bppArgb))using(var g=Configure(preview)) {
                            g.Clear(Color.FromArgb(31,38,34));
                            using(var circle=MaskedIcon(foreground,440,true,false))g.DrawImage(circle,36,36);
                            using(var square=MaskedIcon(foreground,440,false,false))g.DrawImage(square,548,36);
                            using(var themed=MaskedIcon(mono,440,true,true))g.DrawImage(themed,1060,36);
                            using(var font=new Font("Segoe UI",25))using(var brush=new SolidBrush(Color.White)) {
                                g.DrawString("Circular",font,brush,160,515);g.DrawString("Rounded square",font,brush,620,515);g.DrawString("Themed",font,brush,1190,515);
                            }
                            preview.Save(Path.Combine(previews,"launcher-masks.png"),ImageFormat.Png);
                        }
                    }
                    using(var splash=new Bitmap(720,1280,PixelFormat.Format32bppArgb))using(var g=Configure(splash)) {
                        g.Clear(Background);g.DrawImage(foreground,216,496,288,288);splash.Save(Path.Combine(previews,"splash.png"),ImageFormat.Png);
                    }
                }
                return "Original RGB retained for "+retained+" artwork pixels; transparent backdrop; all artwork inside adaptive 64dp safe diameter.";
            }
        }
    }
}
'@
[MoosclesLauncher]::Generate((Resolve-Path -LiteralPath $SourcePath).Path, $projectRoot)
