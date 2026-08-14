> Migrated from `IMAC-ORG/imac-judge-app`#<!---->69 — originally opened by @<!---->DevDavidW on 2025-04-10T02:28:13Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/69

Can we change it to where the "caller" button cancels any other audio that's playing before it starts? In all other cases its good to keep it the way it is. When you press next, and then call to hear a complex figure, you cannot hear the first elements.


---

@<!---->rolfbartels commented on 2025-04-10T04:41:02Z:

So this was the default behaviour, then we added it back because judges where saying that if they where using caller and scoring they did not know what score they had scored and would require a look down away from the plane to ensure thier scores have been taken and they have not made a mistake. E.g 
Judge presses call, then wants to do a 0.5 deduction but instead presses 1., the rest of the figure we scores correctly, he will not know that the score should be a 7.5 rather than 7.0 as he was not alerted to the score change. 
So this behaviour was requested.
We also have the caller audio louder than the figure announcement and score, so we could try lowering the background further

Maybe we can cancel out the figure description ? But keep the  score ?

---

@<!---->DevDavidW commented on 2025-04-11T01:57:48Z:

Ah good point. Yes I think it is more when the audio is playing after clicking Next and the figure name is playing. If the pilot already started the figure and the judge clicks Caller, they miss some of the initial words due to them overplaying each other.

Certainly want to look at this one a little closer to determine if it can be adjusted to work better. Different volume might be just fine for it.

---

@<!---->rolfbartels commented on 2025-04-11T05:21:23Z:

Initially we had the caller button call the figure first then the elements l, maybe we can try cancel the figure audio only but keep the score in the background, the judges still have the written description on the screen, But certainly we should be able to come up with something 

---

@<!---->dpgarceau commented on 2025-11-27T19:48:04Z:

Completed, Nov 2025 - Included in proposed version 2.0
Pressing caller now cancels the figure audio, so that those two don't compete but still allows all other audio to play in tandem to hear scores etc. 
