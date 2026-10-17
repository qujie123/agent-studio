import tkinter as tk
import math

root = tk.Tk()
root.title('Agent demo - no real model call')
canvas = tk.Canvas(root, width=800, height=500, bg='#111827')
canvas.pack()

def draw(t=0):
    canvas.delete('all')
    s = 11 + math.sin(t)
    for i in range(260):
        a = i / 260 * math.tau
        x = 400 + 16 * math.sin(a)**3 * s
        y = 250 - (13*math.cos(a)-5*math.cos(2*a)-2*math.cos(3*a)-math.cos(4*a))*s
        canvas.create_oval(x-3,y-3,x+3,y+3,fill='#f472b6',outline='')
    root.after(30, draw, t+.05)

draw()
root.mainloop()
