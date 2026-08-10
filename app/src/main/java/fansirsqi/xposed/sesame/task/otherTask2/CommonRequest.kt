package fansirsqi.xposed.sesame.task.otherTask2

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.hook.RequestManager.requestString
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.StringUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

class CommonRequest {

    //===========民生之家=============
    //签到
    fun lifeMsgProdSignIn(accessId:String) :JSONObject{
        val method = "alipay.imasp.program.programInvoke"
        val params = "[{\"channel\":\"share\",\"cityCode\":\"\",\"components\":{\"independent_component_sign_in_02378042_independent_component_sign_in\":" +
                "{\"code\":\"SIG2025061003092949\"}},\"extInfo\":{\"riskInfo\":" +
                "{\"consultData\":\"{\\\"captchaId\\\":\\\"$accessId\\\",\\\"bizNo\\\":\\\"\\\",\\\"lang\\\":\\\"zh-CN\\\",\\\"did\\\":\\\"2YLMmaqoHiH63TH=Pe/qLpGmvbqiunDk\\\",\\\"userAgent\\\":\\\"Mozilla/5.0 (Linux; Android 15; 2311DRK48C Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/105.0.5195.148 MYWeb/0.11.0.250416151924 UWS/3.22.2.9999 UCBS/3.22.2.9999_220000000000 Mobile Safari/537.36 ChannelId(6) NebulaSDK/1.8.100112 Nebula AlipayDefined(nt:WIFI,ws:407|0|3.0) AliApp(AP/10.7.26.8100) AlipayClient/10.7.26.8100 Language/zh-Hans useStatusBar/true isConcaveScreen/true Region/CNAriver/1.0.0\\\",\\\"clientType\\\":\\\"web\\\",\\\"version\\\":\\\"2.3.9.1751449998647\\\",\\\"interaction\\\":\\\"DO_NOTHING\\\",\\\"ua\\\":\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"DO_NOTHING\\\\\\\",\\\\\\\"startTime\\\\\\\":0,\\\\\\\"actions\\\\\\\":{\\\\\\\"f\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"k\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"ms\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"mv\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"wc\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"fn\\\\\\\":0,\\\\\\\"kn\\\\\\\":0,\\\\\\\"msn\\\\\\\":0,\\\\\\\"mvn\\\\\\\":0,\\\\\\\"wcn\\\\\\\":0,\\\\\\\"o\\\\\\\":[0,0,0,0,0,0],\\\\\\\"cp\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"dm\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"gy\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"uit\\\\\\\":{}},\\\\\\\"data\\\\\\\":[{\\\\\\\"wbType\\\\\\\":2,\\\\\\\"cipher\\\\\\\":\\\\\\\"4l/FI5nZw7Vr1k5y+JWsQT2IKpa9AABz+jqIrIunPVi2j9KM9ZqYGM/7cqmM6n0tFfKkSuEgIpNr+wpH570zPmzXQZJu=WH1yHXhmsjT=pUpOs8/nvB/JZ0piJXWcLZzzkAqtctnmWKEzzzYcjWJfPJtG2wnA7/rsfFo2/oB9V2BTKh+cyRe8lN9SbJgoSX4RokBpFvgiZLt8A2/Xj6x=88qz6pnz+VufFUnARcTP7jftX8Mw8=CTr0vI+sP8Ojp=l8BtSzfuZl3n7JY2atQEG+AhH7EXQZ39m7lMOLq0UwUtIlDycvsSIGQ3gp5TTnP=Lk+g=8b5BDWUfbZ5oxp63Hx3njO20IFz+cBkzm2C4IRC6JPZGB+zI6Gg6C1cPGzrwzz5p9Cts5hiLXP7iwh33=kBxe5PJ9PstqalEBEA7P95IqSTHfizzgWpByfDkbVL/Y4UNuWe2rL880LWRVCEM1j7u8Vg4x8/s9PZE/g6SL7lQ15EztQGT0mAGZTI0KjDXDlc9RGl3kaliwQqMM9a3+vy9PQF0JuNoVZ44nLwQagg3SB07jKjfDlGpBC00oSP=342oZlqJkgiwHyqYhumOY1w0UQ1qwn18INcee5gtQE6hkyMF4WXy6gD25pvMnILP=+y+oTH=npiPqSsjM2/2OLej8hqnuRrMTH8Ec7zoEUzwh4U=roWj33U0s+OcKnRW1W/of6kmYNoxs0Vyn7KIal0SajeVGlpLaqQAhP=qSNt6iy2TCw+nRSHQYLmjasmwucyVHMBmPZBmoJ80YNJw8kvtbZ4zxghTJOW0ykYvFSC8gmumWHyEMyOX/c=NAqt4BwZ6+4fwHpZ79//RqVcrsSoDcJk+nEBNQnx9R6P7KhfGfrevv5GKRscZ3xo4XOn+PXKXQ8=6Ocz1DJFCEwrwPZI3W0vpB9goSgZ/XyOUQDf8iYtclTuLmxpKDF6LZn6vUSHJh/rJQcaXA3pofkeNrSB2Q866Hi6RYUPDhZnsSIGfn5GzRXANUxhf5=Qs4av5S3fv20oZqyyLKSET5O2Z/=1bqW2JaIZeiuAjlQjFIM2vSDGLp7V/Zkzu94fIRpkB6AZUDYrljz2RV6f0Pepct/Kgr=zyPROI84m3gMO9vFhT808URjKpf+beeMGs+GOlA4Tgk2LVa7kfkfCEVIN6E5ByqQVtlAYQQr1es3Jti4f2qxtJVCZUyc31gYpDhPAqwYqrhapL5xhgzTTarAIUAQq1ohkJvGeV+nU=Wi+=eFQCx2U5JO79Iq+5fDm21H9TnIhvtBCh6vB1a7YfEVE5+jWr5Tr+Lnpcx/9Wq9YFP2BLNP6VEaPk/Gn5rjtOReNUUfxXU2p5C9RIjuDWsnSs0yUOtgpooQyeRPWPIrh1Mi/8zoFBC65uDi1yUhT3vPiWOofpjjS9scza4RyKtEhVlPWYBn+RCcT9P0TgE23kstaHQjvRuPaq5cMZzLZyh6c9yGhtfeqgSZNhqNcRLyxaPZB2X9NaJn=Co3R4vzGz+YQitUgEQp+Av0=HW5cTlfwMoSVT8vf6u6Kt03ZHZjILc=TAl6QOBcPO74Ei4bbaIm/nxI/jyooPNR9iMbN8bAIJkm3+IcE+Sp+8mqs=VuGGR/cZ1T0AfhoLPoJtaMeZC3I9IgzDXpfcXMe0LPb=9++aqoaW1JQ013MBuyO22NHJR=Yv6Ha6iTiab1YR/Ql3rajWb27lXOVO1F8Hnb925YLCAosvyaqeMyA1Khpl9t0PDIQFmryK5hsP80UTIw76O5t7UVSv2RV=F+uxgYm5bY8EVqvt5ghBRA3=PngfE=IIQ2Zk0/KpqzD=AMsCh403ITxDDAahoOCAl1KvC8SOmS3KAbJGBrk9QVuSGbGMa/g8NVg8/zNMIumhXiMsc7/3i/0ROX49bSCgVvr2PT=EEznRt9jOB5GqeKL7avmInZk+746+B0ivy4GSHzVa5YqCpDpD7wl3DMbGCrAHfQA7+DIk+wUWsR/EyQ5MmRkAq3s/Kw2Ojv7xYrEO7Nn59AB8gKc6ycQ9rcSK7LApX/sQF8+vnj2pizeNA17sJlmufa0DZXNGh9cce+9W=wkt6ts2hAgBPSaf8AJ3zx06zVnT2Ku4DEhKTlq7xC/TJizrqu9770p1V4kLlO=VrEJlQF6h3Z83/bMl/ZO9oeBgvqapb87snfnlU0si+LIE6tQtmZWLXalcXwN1A8aT/5QCFISfiyxJhwRqB5WZa4lpWTurCzPEj2YFNwg=0nBnQY=DFxgx4+0o6cpFEXb/opu0qGPm9+UnxxQZW+gaJb1Eir=iK7xon=Ui5btqnklGAyh0U6vjjVJ0T/zx4lWv5F7OGlRwHqFlGB4EB2SyLgfNsrjDWTb3fRG0YXKiJxogqeXr1ZuI/fLA7q3t0z7ghITMvM+nzk3obn4acD0JfL1h+Ckzp7HV5cAEe0TQTg49xJMTvpi4MjQENMhR4SXEhM6+Gny9IfEc92W56vLw5Llm6rj31ZAoWJrTM47BDIkB9hIVVFftVDEw7Jx8II6Yp+9T/WIaChfqZsm=EUbh54L4HbTb0m5NNX5vrN5f5Pvav6uNEFJQqm63hZip41iSV3HSVr8vQwtBpjELFyQMfIG=cIoUa/mAF13zM+zTBsmzAyqDfVz2ScyvnGu+c5RLJku1+=B341eKZBfKCiSzpyZsG00U/kwGQmLDwXe7OGW8Hrxnzwjsv=S=BIktaj2NUpj5q3+iu3z4+Mti/R=vJkOGGg2a+5EZl+/bHoHBtsD8=q67KGpwUFsIP7vsJFUpySYTj31wu2q5k01GcAuxV25oCmrsaE7zho15W0h/+995pmzftFzOvKGrrOOeSoBycCopB5VzhQDSX4Q6A8r/+5j7tBJUCjrLZ3XRfWIPImWpJ4H0sCNYxFar4143meSjvmOBHHS1=gKuyptkIvIDN/yOPXRq9D0XvsxGWBv1PJglW37f+hsbibRF0OETfUwMXGyvkTkGF037HMR1Bu/piLAIqnAEXDgYkn9g++c=kXfNmezP2O19nfTXVb85+5ISclzbBZtVhe/Yna+mzrBSXJ82HGBqLPM/285IoZOn4toLPjofe2hq4L3xWm6zxsX2Qy5Ei4qeSHFi1HnMxh+ktKVSSaC9on6SJsTPwqnCc/RGbhqrwxT8oufcrnpPzxQ6NP+lbJIJA3445EfG2ItBMEp=+ArvTM6e8X3+sJ3zyo+BhWxklEeY0DTPxeDN+bmSnGzP6eZo+Ag+BsWg1l0i6TVcEeXwzYiSsRJ2BhzVvY+p7v2pq+eCPkBorUGnUtStcbutGJgunYQ=Yn06ovnZz3H=cZ59+ocllC+I219q++xWFD+Dk9Afn2QKxPT5NbRio1LVh2N3tDI+=5LoCtwkiTqPIhJIDrv=H/nScZZ25W21Ol2IPMax9qs6C2AGIYQk3Vgf+2kBrDAj=IuD/7CNhFgLUy0wI4WzKXkurDFhtY/oI6CF16FKAcfQOIz1t6hW1Yyt8JAGwYNxs5SYs8kiwnou/GOo49XJ1QVRSiDJP7X2XepHSls0Jzke6L7V5ZQ6ZGoQvovUg3gbJY3Ia/w2VirC7XXcJ46gkNUuZs7Hmh8HVwpngWE=UgtfJGy2JcJZrkQ9tEX/9hspJtN6Z3CNCjaWMz85sNqGPwOWTGg=8A9esKX9CVOK7cLxHS6ATMjieXuz+tfjHs+sbEk4hzhrrwRekvVQqZWpBDZpbNO/XfXWa1ajJS5Ozkm65ZjACIS/4hED9XEh6TLmgTcn8v8bowS=E=LfzKQ9zhVB6Q3yxn=+ANRKnsmqlIZWBgTyeyyxvyvNKtu3UNvcR=t5qpYGUYANaTUf1e0P/goDYZlqYwIkCAP=e1q87ATB7x0f3=UpqwMtucJLa7VBefxKqNemoWNB9kh4t2Dz//D756aJw6Btn3vBl=VkmjCvHk7gieQNJJvxUvvnFr5332srCPL6+Ytc=3MY3yzkUqrho4cmwku2GxAwBni5YPHlKIQz8EcjMrti72iEfwD1G/mU7xuaqjARUY9NX0Qxvta42NiCqVfSq7D/CM/olH1f/RfGGkY5K/JfqNjzbCIlnfkYa2hxFxbx2PVXrO/q4OcM6smhyz7IlwmRpZlXEchpHKom7gmSY1w6O3LA=qz3snj6+R+UkIce5mBZmxW50psVTwPZal5TuP7Aejpwq3XePE4kCtO5J7D=lHr89vnkmY/=+0muNu9rEhGArxQeVZ0YqBnXGgXnpLU7TX9KwTa54uS7s5SYSFB1p40P79HtOzxtHTtvm/E7VM5ED1wMGMhVLcP1/gaUjAM8BrAp6rn9DssHD+5DGPjqNPw3OzANV1IW9D+wv=L7gVY12he5=zqGCEBEBugISNml91m42gotrFWK3k+Ji81zR+bs1O9GDJvU4+LRRh5gq=sUM=6m0Wr+z12tq5kcANOlUVJiLem5MXJOf/1SFRzw0DA2bIT9U5/YXN7L03OLUeAuqt=NklWnE7Xw6T2jThvqBXtXTkj0HuqRB77rf3yUf0jOISi8lO3JZOX6pFmO3U2WAM0BSQAe6oHoopErKxDQelE7sYpo4MDRX2KezTf=MIhj+C7JlsC/0bV3=YZ8xZ2bSca3u3TaFp30JkhvxRzFQDbOU46PQwm446z7JJvO8QPIb1T1xMlI7eYF46FZU274ouhPDj3O5kyFKYLwE9QPy8lgrE6xx+HW/oNRkhnzmpaiuZcRbzpo/kJEX1+2VyaNziQl8Ye13TOTKDZ36xJK0YFfcg8k2N8/=CkiYRQmin4KD0LAcP/l2YgIarjhqFnhZiCM2vHnAKu6j0eT6iWvEj==9M/7KJncCLLSgJKCaICkvWO4xoIvrMyIMQCKacqgP8MfyjpOgJiphOATJDjKiv8bwAwDl2XaHwXfWR3RKB7aNJ8jFaKC1ZvN/KMu2GTGJeMVbF5hcFpTOnT/Q/OkyTKeauMVaALBQ1MNeWJzy4sOf9c6QCmhA7exZgOj01sajCv2BCQc9W5=T=xi/X1e8IPt8VrMoCn1ul2YM/ZavF/A6nqcaEVjY+Y+UCRsMZZy7pPPUb+GkikAvvb7rG5KNsuZlxVklBgEnt74RcJoxXz/jkKwj+9eClEpOWQJ1nnCzIV5Ciqy4zwpU=Fx=IpEOFFwJKXP+HhwaOoVZuto8P3ay2xBltYrFJD04/ly2bhoKyuBowm57rrowEILN0EIDXFGf5S17heKVYnloOV4IsIEA/YUDFtJKncE5WcnIDazUk2ePC6gXTzsNzUohofqofiv8cXmP9EYYv1WhxwsAWOiwMNMVAuKmYAVTFf4pyUHFnksDBOXSWGZpo2568FkE4eVD6WBfjx/K9FrNKUxP1zi7pNH3rUBUEZVquC1NwMXj+sai4Qzeb6MX87S8l5hYVVgR+gC6HMr6FNKJKIeCo++NRRrhZD3iCZInjp8FisJwH9CMYYb/rv8j1OEWVs+NzhQTBTRCkGqL2BXJjYlZACWVYxOtx8r0nEpeJ+j+1GIYKGmn3F1aU7TbBOjVctM=yb9uSTlAkXULL1X/vYNeL55EtRVmEh/F53ComcNvI1PCMn1wE7W3ORb9WemUrwF/7U2fvZGGnZlM40lWA3J+MfoEa2VwT3KVeNBZ0OyYRQzs9lh7jtNWFELr1EK4Zii3BVK+roFwm4DUUsoOLoT=wzclCy7sFqpzfjMPEXuN=nt5=XfBGgNTikEMu/wY6OfZO38dd\\\\\\\"}]}\\\",\\\"scene\\\":\\\"DO_NOTHING\\\",\\\"ts\\\":1758338421485,\\\"encryptedExt\\\":{\\\"wbType\\\":2,\\\"cipher\\\":\\\"C0LGigOye5jKNxL77s8cnfl08psuBgiYRDKVoomJTNjsRwoF5CwpoCuKLIl20843mB/xQTI+GXzLMWr8Z91GUQ6VVbR+2xoQiLy3fiOWgnxw9JVe9k5PQGQr9PnCAN0bqnJO8=fk2F6mbmlxCH9SMmwp2gTGh4Rni3lKRJAHfh6WyJ4LvXtHKKx+SEIqNVwy5=pDTbiQbswp5jS0IAwgsxRFvbOGVTjzlCeM9HKoZJLzzEOu4RoH2yLm/6Aebu8BsX8ZgVrFj9Y1uQoSoSZPfQdd\\\"},\\\"payload\\\":{\\\"wbType\\\":2,\\\"cipher\\\":\\\"=6c/x80nbSY6FwLsMoDYSXzBp857L8+yxErqjXWxRWqq+jC2zK1D6J=Cn+at5H3Z3zzMVq1xtM1WV8RcQL=u8JDTTxcFwQXm3wLHUAkLG4=3vTMpb6jRWlyHvM57VKtekSqQ9nw0pjqSAGqXYHp3k0q1oET7SLi9G5aT9ImNS9E1kTX/5PnqJrKrW60Nxqa8HwY0hf6A74z4bNMjStuL8kCoblJKMPPiwr2c4O53KolRuX6NiWKxgfiStMxK0m1YIahpoOLWPk8UAmKYXuB0eEXF4Q7NXiGcj+48y22e13oXM8KX3OfO/t5b6BL+2mPulSl7tzqYvkJc2gWQiiQjImX4isVYbvcZ3iWU/UUKozmXTlwEPI9lKx5YpSQk2sxhscj=IktMAZiFkSStZ9qpBVCWZ28fFAxpK4E7ZqDDsxii3UmVZYFt4vzQ2gA+ZMmM5h=1eLaxVVmgZwqyZgPOAr6RGOmL=nqry8/G26KYDZosuI6gb0JRwTTk/8HPp7uRMyfg0yDo6o/qfK2X=lEv6A/7cmsci7sEI6Afo+B/9h+hiuAs92KW9p7AEAvEHh7qcpxLx4=7J6FPv543n/CzJwS7Pv0iXOsHfyg/TXQaiez5P167WBLETL5orqia1etNV7X2XfRt14tS32WWzD8xJ4WN76UD21wzxrRSjcPfvHAUfcGtgtya/ohKutx+nXf/gF2lJqr/2EYl3xl7A9mGKVa1OVpnjKa8SeEkPWnYEGVV9RKqWMNf0mcwnwgyxCa8iYokym7n9GbxxyrLV2CLbGKhQL9eOy3zznfHXxqCLmBetUrACcIQ1gEm4LzUHYC7fD/7O8mKax27N=/bK3j5s1OkD1YoHsiGJhiaQ4a6yVmBT=Hph0iSoUJ+lTrGO44YTkpNIBq/LewTnyeIwBPph5ruUIRZEURRgy55xiUZrLnfwppi8CIDaYvj08lFG3W52IWvpNjUUZivb1vjW39gMSNiueTQJBPJ33nw1j5DeVliysjW0lNu=rhNi9plQSJE+kT3+Ylafkt6C/i/5ANit+a8xca0pJgtGNoKhA=4+ZAbcUmGA5NOOCNuJ/oJRKZCuWCB8Nis0gCiP96v5oBXVkQBim2DJo9RmcOQxTCHngAuTCgip/kGz3Y/MMtPJ8HvtQD2pz5EFGwuZ/j23nin6qm+zObpkMwhR+n/e=Qj7BceC0Kan96n4R0NgNUiFemvNKut4XZheJfB8bBwUe5xyiioCoP0sgU0SZJDu6gP2JxT=MT4QFpJkDsCjhb+XyL8J323I2WjWv05BQrnFXOrsf/+qTPP0gEnIT/4iMawDpz1T6+2=FTJs3Q8cRyHuF0WvnZhs875UKr2kHuPS5c3LyLLb40SbfJqB39p=/Auq3hyUIV8JO/oIWwAK+BVBOw1ysXNnb4aIxENv9EWgsJmrM1vvI14auXYRIT9M+4=1IRd\\\"},\\\"s\\\":\\\"A3UdzmgAADBiKMFOz87Ozvn7+azN19bera++uq2mr5D8/v/g//n7//r69/f395D/kJn9qfieuKadpf//voiNo7qJhZ+Zr7i+pYfbjbKfBH9/xmhnwZRIxA8=\\\",\\\"buildId\\\":\\\"5d6f1bf0a0cbfad3b5ae24c5410ea8bb\\\",\\\"apiVersion\\\":\\\"2.0\\\",\\\"triggerEnv\\\":\\\"web\\\"}\",\"scene\":\"CONSULT\"}},\"operationParamIdentify\":\"independent_component_program2025060902501909\",\"source\":\"independent_component_sign_in_02378042_independent_component_sign_in\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //查询任务列表
    fun lifeMsgProdTaskList() :JSONObject{
        val method = "alipay.imasp.program.programInvoke"
        val params = "[{\"channel\":\"share\",\"cityCode\":\"\",\"components\":{\"independent_component_task_reward_02379846_independent_component_task_reward_query\":{}},\"operationParamIdentify\":\"independent_component_program2025060902501909\",\"source\":\"independent_component_task_reward_02379846_independent_component_task_reward_query\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //处理任务
    fun lifeMsgProdTaskHandle(code:String ,recordNo:String,accessId:String) :JSONObject{
        val method = "alipay.imasp.program.programInvoke"
        val params = "[{\"channel\":\"share\",\"cityCode\":\"\",\"components\":{\"independent_component_task_reward_02379846_independent_component_task_reward_process\":" +
                "{\"code\":\"$code\",\"recordNo\":\"$recordNo\"}}," +
                "\"extInfo\":{\"riskInfo\":{\"consultData\":\"" +
                "{\\\"captchaId\\\":\\\"$accessId\\\",\\\"bizNo\\\":\\\"\\\",\\\"lang\\\":\\\"zh-CN\\\",\\\"did\\\":\\\"2YLMmaqoHiH63TH=Pe/qLpGmvbqiunDk\\\",\\\"userAgent\\\":\\\"Mozilla/5.0 (Linux; Android 15; 2311DRK48C Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/105.0.5195.148 MYWeb/0.11.0.250416151924 UWS/3.22.2.9999 UCBS/3.22.2.9999_220000000000 Mobile Safari/537.36 ChannelId(6) NebulaSDK/1.8.100112 Nebula AlipayDefined(nt:WIFI,ws:407|0|3.0) AliApp(AP/10.7.26.8100) AlipayClient/10.7.26.8100 Language/zh-Hans useStatusBar/true isConcaveScreen/true Region/CNAriver/1.0.0\\\",\\\"clientType\\\":\\\"web\\\",\\\"version\\\":\\\"2.3.9.1751449998647\\\",\\\"interaction\\\":\\\"DO_NOTHING\\\",\\\"ua\\\":\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"DO_NOTHING\\\\\\\",\\\\\\\"startTime\\\\\\\":0,\\\\\\\"actions\\\\\\\":{\\\\\\\"f\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"k\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"ms\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"mv\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"wc\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"fn\\\\\\\":0,\\\\\\\"kn\\\\\\\":0,\\\\\\\"msn\\\\\\\":0,\\\\\\\"mvn\\\\\\\":0,\\\\\\\"wcn\\\\\\\":0,\\\\\\\"o\\\\\\\":[0,0,0,0,0,0],\\\\\\\"cp\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"dm\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"gy\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"uit\\\\\\\":{}},\\\\\\\"data\\\\\\\":[{\\\\\\\"wbType\\\\\\\":2,\\\\\\\"cipher\\\\\\\":\\\\\\\"4l/FI5nZw7Vr1k5y+JWsQT2IKpa9AABz+jqIrIunPVi2j9KM9ZqYGM/7cqmM6n0tFfKkSuEgIpNr+wpH570zPmzXQZJu=WH1yHXhmsjT=pUpOs8/nvB/JZ0piJXWcLZzzkAqtctnmWKEzzzYcjWJfPJtG2wnA7/rsfFo2/oB9V2BTKh+cyRe8lN9SbJgoSX4RokBpFvgiZLt8A2/Xj6x=88qz6pnz+VufFUnARcTP7jftX8Mw8=CTr0vI+sP8Ojp=l8BtSzfuZl3n7JY2atQEG+AhH7EXQZ39m7lMOLq0UwUtIlDycvsSIGQ3gp5TTnP=Lk+g=8b5BDWUfbZ5oxp63Hx3njO20IFz+cBkzm2C4IRC6JPZGB+zI6Gg6C1cPGzrwzz5p9Cts5hiLXP7iwh33=kBxe5PJ9PstqalEBEA7P95IqSTHfizzgWpByfDkbVL/Y4UNuWe2rL880LWRVCEM1j7u8Vg4x8/s9PZE/g6SL7lQ15EztQGT0mAGZTI0KjDXDlc9RGl3kaliwQqMM9a3+vy9PQF0JuNoVZ44nLwQagg3SB07jKjfDlGpBC00oSP=342oZlqJkgiwHyqYhumOY1w0UQ1qwn18INcee5gtQE6hkyMF4WXy6gD25pvMnILP=+y+oTH=npiPqSsjM2/2OLej8hqnuRrMTH8Ec7zoEUzwh4U=roWj33U0s+OcKnRW1W/of6kmYNoxs0Vyn7KIal0SajeVGlpLaqQAhP=qSNt6iy2TCw+nRSHQYLmjasmwucyVHMBmPZBmoJ80YNJw8kvtbZ4zxghTJOW0ykYvFSC8gmumWHyEMyOX/c=NAqt4BwZ6+4fwHpZ79//RqVcrsSoDcJk+nEBNQnx9R6P7KhfGfrevv5GKRscZ3xo4XOn+PXKXQ8=6Ocz1DJFCEwrwPZI3W0vpB9goSgZ/XyOUQDf8iYtclTuLmxpKDF6LZn6vUSHJh/rJQcaXA3pofkeNrSB2Q866Hi6RYUPDhZnsSIGfn5GzRXANUxhf5=Qs4av5S3fv20oZqyyLKSET5O2Z/=1bqW2JaIZeiuAjlQjFIM2vSDGLp7V/Zkzu94fIRpkB6AZUDYrljz2RV6f0Pepct/Kgr=zyPROI84m3gMO9vFhT808URjKpf+beeMGs+GOlA4Tgk2LVa7kfkfCEVIN6E5ByqQVtlAYQQr1es3Jti4f2qxtJVCZUyc31gYpDhPAqwYqrhapL5xhgzTTarAIUAQq1ohkJvGeV+nU=Wi+=eFQCx2U5JO79Iq+5fDm21H9TnIhvtBCh6vB1a7YfEVE5+jWr5Tr+Lnpcx/9Wq9YFP2BLNP6VEaPk/Gn5rjtOReNUUfxXU2p5C9RIjuDWsnSs0yUOtgpooQyeRPWPIrh1Mi/8zoFBC65uDi1yUhT3vPiWOofpjjS9scza4RyKtEhVlPWYBn+RCcT9P0TgE23kstaHQjvRuPaq5cMZzLZyh6c9yGhtfeqgSZNhqNcRLyxaPZB2X9NaJn=Co3R4vzGz+YQitUgEQp+Av0=HW5cTlfwMoSVT8vf6u6Kt03ZHZjILc=TAl6QOBcPO74Ei4bbaIm/nxI/jyooPNR9iMbN8bAIJkm3+IcE+Sp+8mqs=VuGGR/cZ1T0AfhoLPoJtaMeZC3I9IgzDXpfcXMe0LPb=9++aqoaW1JQ013MBuyO22NHJR=Yv6Ha6iTiab1YR/Ql3rajWb27lXOVO1F8Hnb925YLCAosvyaqeMyA1Khpl9t0PDIQFmryK5hsP80UTIw76O5t7UVSv2RV=F+uxgYm5bY8EVqvt5ghBRA3=PngfE=IIQ2Zk0/KpqzD=AMsCh403ITxDDAahoOCAl1KvC8SOmS3KAbJGBrk9QVuSGbGMa/g8NVg8/zNMIumhXiMsc7/3i/0ROX49bSCgVvr2PT=EEznRt9jOB5GqeKL7avmInZk+746+B0ivy4GSHzVa5YqCpDpD7wl3DMbGCrAHfQA7+DIk+wUWsR/EyQ5MmRkAq3s/Kw2Ojv7xYrEO7Nn59AB8gKc6ycQ9rcSK7LApX/sQF8+vnj2pizeNA17sJlmufa0DZXNGh9cce+9W=wkt6ts2hAgBPSaf8AJ3zx06zVnT2Ku4DEhKTlq7xC/TJizrqu9770p1V4kLlO=VrEJlQF6h3Z83/bMl/ZO9oeBgvqapb87snfnlU0si+LIE6tQtmZWLXalcXwN1A8aT/5QCFISfiyxJhwRqB5WZa4lpWTurCzPEj2YFNwg=0nBnQY=DFxgx4+0o6cpFEXb/opu0qGPm9+UnxxQZW+gaJb1Eir=iK7xon=Ui5btqnklGAyh0U6vjjVJ0T/zx4lWv5F7OGlRwHqFlGB4EB2SyLgfNsrjDWTb3fRG0YXKiJxogqeXr1ZuI/fLA7q3t0z7ghITMvM+nzk3obn4acD0JfL1h+Ckzp7HV5cAEe0TQTg49xJMTvpi4MjQENMhR4SXEhM6+Gny9IfEc92W56vLw5Llm6rj31ZAoWJrTM47BDIkB9hIVVFftVDEw7Jx8II6Yp+9T/WIaChfqZsm=EUbh54L4HbTb0m5NNX5vrN5f5Pvav6uNEFJQqm63hZip41iSV3HSVr8vQwtBpjELFyQMfIG=cIoUa/mAF13zM+zTBsmzAyqDfVz2ScyvnGu+c5RLJku1+=B341eKZBfKCiSzpyZsG00U/kwGQmLDwXe7OGW8Hrxnzwjsv=S=BIktaj2NUpj5q3+iu3z4+Mti/R=vJkOGGg2a+5EZl+/bHoHBtsD8=q67KGpwUFsIP7vsJFUpySYTj31wu2q5k01GcAuxV25oCmrsaE7zho15W0h/+995pmzftFzOvKGrrOOeSoBycCopB5VzhQDSX4Q6A8r/+5j7tBJUCjrLZ3XRfWIPImWpJ4H0sCNYxFar4143meSjvmOBHHS1=gKuyptkIvIDN/yOPXRq9D0XvsxGWBv1PJglW37f+hsbibRF0OETfUwMXGyvkTkGF037HMR1Bu/piLAIqnAEXDgYkn9g++c=kXfNmezP2O19nfTXVb85+5ISclzbBZtVhe/Yna+mzrBSXJ82HGBqLPM/285IoZOn4toLPjofe2hq4L3xWm6zxsX2Qy5Ei4qeSHFi1HnMxh+ktKVSSaC9on6SJsTPwqnCc/RGbhqrwxT8oufcrnpPzxQ6NP+lbJIJA3445EfG2ItBMEp=+ArvTM6e8X3+sJ3zyo+BhWxklEeY0DTPxeDN+bmSnGzP6eZo+Ag+BsWg1l0i6TVcEeXwzYiSsRJ2BhzVvY+p7v2pq+eCPkBorUGnUtStcbutGJgunYQ=Yn06ovnZz3H=cZ59+ocllC+I219q++xWFD+Dk9Afn2QKxPT5NbRio1LVh2N3tDI+=5LoCtwkiTqPIhJIDrv=H/nScZZ25W21Ol2IPMax9qs6C2AGIYQk3Vgf+2kBrDAj=IuD/7CNhFgLUy0wI4WzKXkurDFhtY/oI6CF16FKAcfQOIz1t6hW1Yyt8JAGwYNxs5SYs8kiwnou/GOo49XJ1QVRSiDJP7X2XepHSls0Jzke6L7V5ZQ6ZGoQvovUg3gbJY3Ia/w2VirC7XXcJ46gkNUuZs7Hmh8HVwpngWE=UgtfJGy2JcJZrkQ9tEX/9hspJtN6Z3CNCjaWMz85sNqGPwOWTGg=8A9esKX9CVOK7cLxHS6ATMjieXuz+tfjHs+sbEk4hzhrrwRekvVQqZWpBDZpbNO/XfXWa1ajJS5Ozkm65ZjACIS/4hED9XEh6TLmgTcn8v8bowS=E=LfzKQ9zhVB6Q3yxn=+ANRKnsmqlIZWBgTyeyyxvyvNKtu3UNvcR=t5qpYGUYANaTUf1e0P/goDYZlqYwIkCAP=e1q87ATB7x0f3=UpqwMtucJLa7VBefxKqNemoWNB9kh4t2Dz//D756aJw6Btn3vBl=VkmjCvHk7gieQNJJvxUvvnFr5332srCPL6+Ytc=3MY3yzkUqrho4cmwku2GxAwBni5YPHlKIQz8EcjMrti72iEfwD1G/mU7xuaqjARUY9NX0Qxvta42NiCqVfSq7D/CM/olH1f/RfGGkY5K/JfqNjzbCIlnfkYa2hxFxbx2PVXrO/q4OcM6smhyz7IlwmRpZlXEchpHKom7gmSY1w6O3LA=qz3snj6+R+UkIce5mBZmxW50psVTwPZal5TuP7Aejpwq3XePE4kCtO5J7D=lHr89vnkmY/=+0muNu9rEhGArxQeVZ0YqBnXGgXnpLU7TX9KwTa54uS7s5SYSFB1p40P79HtOzxtHTtvm/E7VM5ED1wMGMhVLcP1/gaUjAM8BrAp6rn9DssHD+5DGPjqNPw3OzANV1IW9D+wv=L7gVY12he5=zqGCEBEBugISNml91m42gotrFWK3k+Ji81zR+bs1O9GDJvU4+LRRh5gq=sUM=6m0Wr+z12tq5kcANOlUVJiLem5MXJOf/1SFRzw0DA2bIT9U5/YXN7L03OLUeAuqt=NklWnE7Xw6T2jThvqBXtXTkj0HuqRB77rf3yUf0jOISi8lO3JZOX6pFmO3U2WAM0BSQAe6oHoopErKxDQelE7sYpo4MDRX2KezTf=MIhj+C7JlsC/0bV3=YZ8xZ2bSca3u3TaFp30JkhvxRzFQDbOU46PQwm446z7JJvO8QPIb1T1xMlI7eYF46FZU274ouhPDj3O5kyFKYLwE9QPy8lgrE6xx+HW/oNRkhnzmpaiuZcRbzpo/kJEX1+2VyaNziQl8Ye13TOTKDZ36xJK0YFfcg8k2N8/=CkiYRQmin4KD0LAcP/l2YgIarjhqFnhZiCM2vHnAKu6j0eT6iWvEj==9M/7KJncCLLSgJKCaICkvWO4xoIvrMyIMQCKacqgP8MfyjpOgJiphOATJDjKiv8bwAwDl2XaHwXfWR3RKB7aNJ8jFaKC1ZvN/KMu2GTGJeMVbF5hcFpTOnT/Q/OkyTKeauMVaALBQ1MNeWJzy4sOf9c6QCmhA7exZgOj01sajCv2BCQc9W5=T=xi/X1e8IPt8VrMoCn1ul2YM/ZavF/A6nqcaEVjY+Y+UCRsMZZy7pPPUb+GkikAvvb7rG5KNsuZlxVklBgEnt74RcJoxXz/jkKwj+9eClEpOWQJ1nnCzIV5Ciqy4zwpU=Fx=IpEOFFwJKXP+HhwaOoVZuto8P3ay2xBltYrFJD04/ly2bhoKyuBowm57rrowEILN0EIDXFGf5S17heKVYnloOV4IsIEA/YUDFtJKncE5WcnIDazUk2ePC6gXTzsNzUohofqofiv8cXmP9EYYv1WhxwsAWOiwMNMVAuKmYAVTFf4pyUHFnksDBOXSWGZpo2568FkE4eVD6WBfjx/K9FrNKUxP1zi7pNH3rUBUEZVquC1NwMXj+sai4Qzeb6MX87S8l5hYVVgR+gC6HMr6FNKJKIeCo++NRRrhZD3iCZInjp8FisJwH9CMYYb/rv8j1OEWVs+NzhQTBTRCkGqL2BXJjYlZACWVYxOtx8r0nEpeJ+j+1GIYKGmn3F1aU7TbBOjVctM=yb9uSTlAkXULL1X/vYNeL55EtRVmEh/F53ComcNvI1PCMn1wE7W3ORb9WemUrwF/7U2fvZGGnZlM40lWA3J+MfoEa2VwT3KVeNBZ0OyYRQzs9lh7jtNWFELr1EK4Zii3BVK+roFwm4DUUsoOLoT=wzclCy7sFqpzfjMPEXuN=nt5=XfBGgNTikEMu/wY6OfZO38dd\\\\\\\"}]}\\\",\\\"scene\\\":\\\"DO_NOTHING\\\",\\\"ts\\\":1758338612137,\\\"encryptedExt\\\":{\\\"wbType\\\":2,\\\"cipher\\\":\\\"C0LGigOye5jKNxL77s8cnfl08psuBgiYRDKVoomJTNjsRwoF5CwpoCuKLIl20843mB/xQTI+GXzLMWr8Z91GUQ6VVbR+2xoQiLy3fiOWgnLjzVsnlGEk1aMjqSKwz78zyEeaI+XRl6PRt2hGlI=6JPeBUA0rngGLGQY2TZqbAry6WMiwmDtK0zX4mZhTOTrYrTwowJWIZIQ6eB=fC/y1IAp6Ob10q/AbN8ZvavX2SC+83CKBFwISDB5h1X9CeNVZhXWTrg8=oaeW01cPJTBeG8dd\\\"},\\\"payload\\\":{\\\"wbType\\\":2,\\\"cipher\\\":\\\"=6c/x80nbSY6FwLsMoDYSXzBp857L8+yxErqjXWxRWqq+jC2zK1D6J=Cn+at5H3Z3zzMVq1xtM1WV8RcQL=u8JDTTxcFwQXm3wLHUAkLG4=3vTMpb6jRWlyHvM57VKtekSqQ9nw0pjqSAGqXYHp3k0q1oET7SLi9G5aT9ImNS9E1kTX/5PnqJrKrW60Nxqa8HwY0hf6A74z4bNMjStuL8kCoblJKMPPiwr2c4O53KolRuX6NiWKxgfiStMxK0m1YIahpoOLWPk8UAmKYXuB0eEXF4Q7NXiGcj+48y22e13oXM8KX3OfO/t5b6BL+2mPulSl7tzqYvkJc2gWQiiQjImX4isVYbvcZ3iWU/UUKozmXTlwEPI9lKx5YpSQk2sxhscj=IktMAZiFkSStZ9qpBVCWZ28fFAxpK4E7ZqDDsxii3UmVZYFt4vzQ2gA+ZMmM5h=1eLaxVVmgZwqyZgPOAr6RGOmL=nqry8/G26KYDZosuI6gb0JRwTTk/8HPp7uRMyfg0yDo6o/qfK2X=lEv6A/7cmsci7sEI6Afo+B/9h+hiuAs92KW9p7AEAvEHh7qcpxLx4=7J6FPv543n/CzJwS7Pv0iXOsHfyg/TXQaiez5P167WBLETL5orqia1etNV7X2XfRt14tS32WWzD8xJ4WN76UD21wzxrRSjcPfvHAUfcGtgtya/ohKutx+nXf/gF2lJqr/2EYl3xl7A9mGKVa1OVpnjKa8SeEkPWnYEGVV9RKqWMNf0mcwnwgyxCa8iYokym7n9GbxxyrLV2CLbGKhQL9eOy3zznfHXxqCLmBetUrACcIQ1gEm4LzUHYC7fD/7O8mKax27N=/bK3j5s1OkD1YoHsiGJhiaQ4a6yVmBT=Hph0iSoUJ+lTrGO44YTkpNIBq/LewTnyeIwBPph5ruUIRZEURRgy55xiUZrLnfwppi8CIDaYvj08lFG3W52IWvpNjUUZivb1vjW39gMSNiueTQJBPJ33nw1j5DeVliysjW0lNu=rhNi9plQSJE+kT3+Ylafkt6C/i/5ANit+a8xca0pJgtGNoKhA=4+ZAbcUmGA5NOOCNuJ/oJRKZCuWCB8Nis0gCiP96v5oBXVkQBim2DJo9RmcOQxTCHngAuTCgip/kGz3Y/MMtPJ8HvtQD2pz5EFGwuZ/j23nin6qm+zObpkMwhR+n/e=Qj7BceC0Kan96n4R0NgNUiFemvNKut4XZheJfB8bBwUe5xyiioCoP0sgU0SZJDu6gP2JxT=MT4QFpJkDsCjhb+XyL8J323I2WjWv05BQrnFXOrsf/+qTPP0gEnIT/4iMawDpz1T6+2=FTJs3Q8cRyHuF0WvnZhs875UKr2kHuPS5c3LyLLb40SbfJqB39p=/Auq3hyUIV8JO/oIWwAK+BVBOw1ysXNnb4aIxENv9EWgsJmrM1vvI14auXYRIT9M+4=1IRd\\\"},\\\"s\\\":\\\"AzQezmgAAAQj6OwMjoyMjLu5u+6PlZSc7+38+O/k7dK+vL2ivbu5vbi4tbW1tdK90tu/67rc+uTf5729/MrP4fjLx93b7fr852gi+4GLvERsXaP+ATyjTP4=\\\",\\\"buildId\\\":\\\"5d6f1bf0a0cbfad3b5ae24c5410ea8bb\\\",\\\"apiVersion\\\":\\\"2.0\\\",\\\"triggerEnv\\\":\\\"web\\\"}\",\"scene\":\"CONSULT\"}},\"operationParamIdentify\":\"independent_component_program2025060902501909\",\"source\":\"independent_component_task_reward_02379846_independent_component_task_reward_process\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    // 获取用户信息
    fun lifeMsgProdUserInfo() :JSONObject{
        val method = "com.alipay.industrydoraemon.biz.bigcity.rpc.queryEnergy"
        val params = "[{}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    // 游戏任务
    fun lifeMsgProdGame(campInfo: String) :JSONObject{
        val method = "alipay.promoprod.camp.promokernel.trigger"
        val params = "[{\"campInfo\":\"${campInfo}\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //领取活跃奖励
    fun lifeMsgProdActive(code: String) :JSONObject{
        val method = "alipay.imasp.program.programInvoke"
        val params = "[{\"channel\":\"share\",\"cityCode\":\"\",\"components\":{\"independent_component_luckdraw_02379583_industry_luckdraw_action\":" +
                "{\"code\":\"${code}\",\"consultAfterLuckDraw\":\"false\",\"skipLuckDrawConsult\":\"true\"}},\"operationParamIdentify\":\"independent_component_program2025060902501909\",\"source\":\"independent_component_luckdraw_02379583_industry_luckdraw_action\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //一键领取建筑建成奖励
    fun lifeMsgProdPickUpEnergy() :JSONObject{
        val method = "alipay.imasp.program.programInvoke"
        val params = "[{\"cityCode\":\"450500\",\"components\":{\"independent_component_luckdraw_02570447_industry_luckdraw_action\":{\"code\":\"LDP2025081103328179\",\"consultAfterLuckDraw\":\"false\",\"skipLuckDrawConsult\":\"true\"}},\"operationParamIdentify\":\"independent_component_program2025060902501909\",\"source\":\"independent_component_luckdraw_02570447_industry_luckdraw_action\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //查询活跃情况
    fun lifeMsgProdActiveQuery() :JSONObject{
        val method = "alipay.imasp.program.programInvoke"
        val params = "[{\"channel\":\"share\",\"cityCode\":\"\",\"components\":{\"independent_component_luckdraw_02379583_independent_component_luck_draw_query\":{\"consultAfterLuckDraw\":\"true\",\"skipRecallRecentOrder\":\"true\"}},\"operationParamIdentify\":\"independent_component_program2025060902501909\",\"source\":\"independent_component_luckdraw_02379583_independent_component_luck_draw_query\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //查询当前建筑情况
    fun lifeMsgProdBuildingQuery() :JSONObject{
        val method = "com.alipay.industrydoraemon.biz.bigcity.rpc.queryUserBuildingInfo"
        val params = "[{}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //领取建筑奖励
    fun lifeMsgProdBuildingReward(accessId: String) :JSONObject{
        val method = "alipay.imasp.program.programInvoke"
        val params = "[{\"channel\":\"share\",\"cityCode\":\"\",\"components\":{\"independent_component_luckdraw_02378770_industry_luckdraw_action\":" +
                "{\"code\":\"LDP2025061003092661\",\"consultAfterLuckDraw\":\"false\",\"skipLuckDrawConsult\":\"true\"}},\"extInfo\":{\"riskInfo\":" +
                "{\"consultData\":\"{\\\"captchaId\\\":\\\"$accessId\\\",\\\"bizNo\\\":\\\"\\\",\\\"lang\\\":\\\"zh-CN\\\",\\\"did\\\":\\\"2YLMmaqoHiH63TH=Pe/qLpGmvbqiunDk\\\",\\\"userAgent\\\":\\\"Mozilla/5.0 (Linux; Android 15; 2311DRK48C Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/105.0.5195.148 MYWeb/0.11.0.250416151924 UWS/3.22.2.9999 UCBS/3.22.2.9999_220000000000 Mobile Safari/537.36 ChannelId(6) NebulaSDK/1.8.100112 Nebula AlipayDefined(nt:WIFI,ws:407|0|3.0) AliApp(AP/10.7.26.8100) AlipayClient/10.7.26.8100 Language/zh-Hans useStatusBar/true isConcaveScreen/true Region/CNAriver/1.0.0\\\",\\\"clientType\\\":\\\"web\\\",\\\"version\\\":\\\"2.3.9.1751449998647\\\",\\\"interaction\\\":\\\"DO_NOTHING\\\",\\\"ua\\\":\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"DO_NOTHING\\\\\\\",\\\\\\\"startTime\\\\\\\":0,\\\\\\\"actions\\\\\\\":{\\\\\\\"f\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"k\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"ms\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"mv\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"wc\\\\\\\":\\\\\\\"AAA=\\\\\\\",\\\\\\\"fn\\\\\\\":0,\\\\\\\"kn\\\\\\\":0,\\\\\\\"msn\\\\\\\":0,\\\\\\\"mvn\\\\\\\":0,\\\\\\\"wcn\\\\\\\":0,\\\\\\\"o\\\\\\\":[0,0,0,0,0,0],\\\\\\\"cp\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"dm\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"gy\\\\\\\":\\\\\\\"WScd\\\\\\\",\\\\\\\"uit\\\\\\\":{}},\\\\\\\"data\\\\\\\":[{\\\\\\\"wbType\\\\\\\":2,\\\\\\\"cipher\\\\\\\":\\\\\\\"4l/FI5nZw7Vr1k5y+JWsQT2IKpa9AABz+jqIrIunPVi2j9KM9ZqYGM/7cqmM6n0tFfKkSuEgIpNr+wpH570zPmzXQZJu=WH1yHXhmsjT=pUpOs8/nvB/JZ0piJXWcLZzzkAqtctnmWKEzzzYcjWJfPJtG2wnA7/rsfFo2/oB9V2BTKh+cyRe8lN9SbJgoSX4RokBpFvgiZLt8A2/Xj6x=88qz6pnz+VufFUnARcTP7jftX8Mw8=CTr0vI+sP8Ojp=l8BtSzfuZl3n7JY2atQEG+AhH7EXQZ39m7lMOLq0UwUtIlDycvsSIGQ3gp5TTnP=Lk+g=8b5BDWUfbZ5oxp63Hx3njO20IFz+cBkzm2C4IRC6JPZGB+zI6Gg6C1cPGzrwzz5p9Cts5hiLXP7iwh33=kBxe5PJ9PstqalEBEA7P95IqSTHfizzgWpByfDkbVL/Y4UNuWe2rL880LWRVCEM1j7u8Vg4x8/s9PZE/g6SL7lQ15EztQGT0mAGZTI0KjDXDlc9RGl3kaliwQqMM9a3+vy9PQF0JuNoVZ44nLwQagg3SB07jKjfDlGpBC00oSP=342oZlqJkgiwHyqYhumOY1w0UQ1qwn18INcee5gtQE6hkyMF4WXy6gD25pvMnILP=+y+oTH=npiPqSsjM2/2OLej8hqnuRrMTH8Ec7zoEUzwh4U=roWj33U0s+OcKnRW1W/of6kmYNoxs0Vyn7KIal0SajeVGlpLaqQAhP=qSNt6iy2TCw+nRSHQYLmjasmwucyVHMBmPZBmoJ80YNJw8kvtbZ4zxghTJOW0ykYvFSC8gmumWHyEMyOX/c=NAqt4BwZ6+4fwHpZ79//RqVcrsSoDcJk+nEBNQnx9R6P7KhfGfrevv5GKRscZ3xo4XOn+PXKXQ8=6Ocz1DJFCEwrwPZI3W0vpB9goSgZ/XyOUQDf8iYtclTuLmxpKDF6LZn6vUSHJh/rJQcaXA3pofkeNrSB2Q866Hi6RYUPDhZnsSIGfn5GzRXANUxhf5=Qs4av5S3fv20oZqyyLKSET5O2Z/=1bqW2JaIZeiuAjlQjFIM2vSDGLp7V/Zkzu94fIRpkB6AZUDYrljz2RV6f0Pepct/Kgr=zyPROI84m3gMO9vFhT808URjKpf+beeMGs+GOlA4Tgk2LVa7kfkfCEVIN6E5ByqQVtlAYQQr1es3Jti4f2qxtJVCZUyc31gYpDhPAqwYqrhapL5xhgzTTarAIUAQq1ohkJvGeV+nU=Wi+=eFQCx2U5JO79Iq+5fDm21H9TnIhvtBCh6vB1a7YfEVE5+jWr5Tr+Lnpcx/9Wq9YFP2BLNP6VEaPk/Gn5rjtOReNUUfxXU2p5C9RIjuDWsnSs0yUOtgpooQyeRPWPIrh1Mi/8zoFBC65uDi1yUhT3vPiWOofpjjS9scza4RyKtEhVlPWYBn+RCcT9P0TgE23kstaHQjvRuPaq5cMZzLZyh6c9yGhtfeqgSZNhqNcRLyxaPZB2X9NaJn=Co3R4vzGz+YQitUgEQp+Av0=HW5cTlfwMoSVT8vf6u6Kt03ZHZjILc=TAl6QOBcPO74Ei4bbaIm/nxI/jyooPNR9iMbN8bAIJkm3+IcE+Sp+8mqs=VuGGR/cZ1T0AfhoLPoJtaMeZC3I9IgzDXpfcXMe0LPb=9++aqoaW1JQ013MBuyO22NHJR=Yv6Ha6iTiab1YR/Ql3rajWb27lXOVO1F8Hnb925YLCAosvyaqeMyA1Khpl9t0PDIQFmryK5hsP80UTIw76O5t7UVSv2RV=F+uxgYm5bY8EVqvt5ghBRA3=PngfE=IIQ2Zk0/KpqzD=AMsCh403ITxDDAahoOCAl1KvC8SOmS3KAbJGBrk9QVuSGbGMa/g8NVg8/zNMIumhXiMsc7/3i/0ROX49bSCgVvr2PT=EEznRt9jOB5GqeKL7avmInZk+746+B0ivy4GSHzVa5YqCpDpD7wl3DMbGCrAHfQA7+DIk+wUWsR/EyQ5MmRkAq3s/Kw2Ojv7xYrEO7Nn59AB8gKc6ycQ9rcSK7LApX/sQF8+vnj2pizeNA17sJlmufa0DZXNGh9cce+9W=wkt6ts2hAgBPSaf8AJ3zx06zVnT2Ku4DEhKTlq7xC/TJizrqu9770p1V4kLlO=VrEJlQF6h3Z83/bMl/ZO9oeBgvqapb87snfnlU0si+LIE6tQtmZWLXalcXwN1A8aT/5QCFISfiyxJhwRqB5WZa4lpWTurCzPEj2YFNwg=0nBnQY=DFxgx4+0o6cpFEXb/opu0qGPm9+UnxxQZW+gaJb1Eir=iK7xon=Ui5btqnklGAyh0U6vjjVJ0T/zx4lWv5F7OGlRwHqFlGB4EB2SyLgfNsrjDWTb3fRG0YXKiJxogqeXr1ZuI/fLA7q3t0z7ghITMvM+nzk3obn4acD0JfL1h+Ckzp7HV5cAEe0TQTg49xJMTvpi4MjQENMhR4SXEhM6+Gny9IfEc92W56vLw5Llm6rj31ZAoWJrTM47BDIkB9hIVVFftVDEw7Jx8II6Yp+9T/WIaChfqZsm=EUbh54L4HbTb0m5NNX5vrN5f5Pvav6uNEFJQqm63hZip41iSV3HSVr8vQwtBpjELFyQMfIG=cIoUa/mAF13zM+zTBsmzAyqDfVz2ScyvnGu+c5RLJku1+=B341eKZBfKCiSzpyZsG00U/kwGQmLDwXe7OGW8Hrxnzwjsv=S=BIktaj2NUpj5q3+iu3z4+Mti/R=vJkOGGg2a+5EZl+/bHoHBtsD8=q67KGpwUFsIP7vsJFUpySYTj31wu2q5k01GcAuxV25oCmrsaE7zho15W0h/+995pmzftFzOvKGrrOOeSoBycCopB5VzhQDSX4Q6A8r/+5j7tBJUCjrLZ3XRfWIPImWpJ4H0sCNYxFar4143meSjvmOBHHS1=gKuyptkIvIDN/yOPXRq9D0XvsxGWBv1PJglW37f+hsbibRF0OETfUwMXGyvkTkGF037HMR1Bu/piLAIqnAEXDgYkn9g++c=kXfNmezP2O19nfTXVb85+5ISclzbBZtVhe/Yna+mzrBSXJ82HGBqLPM/285IoZOn4toLPjofe2hq4L3xWm6zxsX2Qy5Ei4qeSHFi1HnMxh+ktKVSSaC9on6SJsTPwqnCc/RGbhqrwxT8oufcrnpPzxQ6NP+lbJIJA3445EfG2ItBMEp=+ArvTM6e8X3+sJ3zyo+BhWxklEeY0DTPxeDN+bmSnGzP6eZo+Ag+BsWg1l0i6TVcEeXwzYiSsRJ2BhzVvY+p7v2pq+eCPkBorUGnUtStcbutGJgunYQ=Yn06ovnZz3H=cZ59+ocllC+I219q++xWFD+Dk9Afn2QKxPT5NbRio1LVh2N3tDI+=5LoCtwkiTqPIhJIDrv=H/nScZZ25W21Ol2IPMax9qs6C2AGIYQk3Vgf+2kBrDAj=IuD/7CNhFgLUy0wI4WzKXkurDFhtY/oI6CF16FKAcfQOIz1t6hW1Yyt8JAGwYNxs5SYs8kiwnou/GOo49XJ1QVRSiDJP7X2XepHSls0Jzke6L7V5ZQ6ZGoQvovUg3gbJY3Ia/w2VirC7XXcJ46gkNUuZs7Hmh8HVwpngWE=UgtfJGy2JcJZrkQ9tEX/9hspJtN6Z3CNCjaWMz85sNqGPwOWTGg=8A9esKX9CVOK7cLxHS6ATMjieXuz+tfjHs+sbEk4hzhrrwRekvVQqZWpBDZpbNO/XfXWa1ajJS5Ozkm65ZjACIS/4hED9XEh6TLmgTcn8v8bowS=E=LfzKQ9zhVB6Q3yxn=+ANRKnsmqlIZWBgTyeyyxvyvNKtu3UNvcR=t5qpYGUYANaTUf1e0P/goDYZlqYwIkCAP=e1q87ATB7x0f3=UpqwMtucJLa7VBefxKqNemoWNB9kh4t2Dz//D756aJw6Btn3vBl=VkmjCvHk7gieQNJJvxUvvnFr5332srCPL6+Ytc=3MY3yzkUqrho4cmwku2GxAwBni5YPHlKIQz8EcjMrti72iEfwD1G/mU7xuaqjARUY9NX0Qxvta42NiCqVfSq7D/CM/olH1f/RfGGkY5K/JfqNjzbCIlnfkYa2hxFxbx2PVXrO/q4OcM6smhyz7IlwmRpZlXEchpHKom7gmSY1w6O3LA=qz3snj6+R+UkIce5mBZmxW50psVTwPZal5TuP7Aejpwq3XePE4kCtO5J7D=lHr89vnkmY/=+0muNu9rEhGArxQeVZ0YqBnXGgXnpLU7TX9KwTa54uS7s5SYSFB1p40P79HtOzxtHTtvm/E7VM5ED1wMGMhVLcP1/gaUjAM8BrAp6rn9DssHD+5DGPjqNPw3OzANV1IW9D+wv=L7gVY12he5=zqGCEBEBugISNml91m42gotrFWK3k+Ji81zR+bs1O9GDJvU4+LRRh5gq=sUM=6m0Wr+z12tq5kcANOlUVJiLem5MXJOf/1SFRzw0DA2bIT9U5/YXN7L03OLUeAuqt=NklWnE7Xw6T2jThvqBXtXTkj0HuqRB77rf3yUf0jOISi8lO3JZOX6pFmO3U2WAM0BSQAe6oHoopErKxDQelE7sYpo4MDRX2KezTf=MIhj+C7JlsC/0bV3=YZ8xZ2bSca3u3TaFp30JkhvxRzFQDbOU46PQwm446z7JJvO8QPIb1T1xMlI7eYF46FZU274ouhPDj3O5kyFKYLwE9QPy8lgrE6xx+HW/oNRkhnzmpaiuZcRbzpo/kJEX1+2VyaNziQl8Ye13TOTKDZ36xJK0YFfcg8k2N8/=CkiYRQmin4KD0LAcP/l2YgIarjhqFnhZiCM2vHnAKu6j0eT6iWvEj==9M/7KJncCLLSgJKCaICkvWO4xoIvrMyIMQCKacqgP8MfyjpOgJiphOATJDjKiv8bwAwDl2XaHwXfWR3RKB7aNJ8jFaKC1ZvN/KMu2GTGJeMVbF5hcFpTOnT/Q/OkyTKeauMVaALBQ1MNeWJzy4sOf9c6QCmhA7exZgOj01sajCv2BCQc9W5=T=xi/X1e8IPt8VrMoCn1ul2YM/ZavF/A6nqcaEVjY+Y+UCRsMZZy7pPPUb+GkikAvvb7rG5KNsuZlxVklBgEnt74RcJoxXz/jkKwj+9eClEpOWQJ1nnCzIV5Ciqy4zwpU=Fx=IpEOFFwJKXP+HhwaOoVZuto8P3ay2xBltYrFJD04/ly2bhoKyuBowm57rrowEILN0EIDXFGf5S17heKVYnloOV4IsIEA/YUDFtJKncE5WcnIDazUk2ePC6gXTzsNzUohofqofiv8cXmP9EYYv1WhxwsAWOiwMNMVAuKmYAVTFf4pyUHFnksDBOXSWGZpo2568FkE4eVD6WBfjx/K9FrNKUxP1zi7pNH3rUBUEZVquC1NwMXj+sai4Qzeb6MX87S8l5hYVVgR+gC6HMr6FNKJKIeCo++NRRrhZD3iCZInjp8FisJwH9CMYYb/rv8j1OEWVs+NzhQTBTRCkGqL2BXJjYlZACWVYxOtx8r0nEpeJ+j+1GIYKGmn3F1aU7TbBOjVctM=yb9uSTlAkXULL1X/vYNeL55EtRVmEh/F53ComcNvI1PCMn1wE7W3ORb9WemUrwF/7U2fvZGGnZlM40lWA3J+MfoEa2VwT3KVeNBZ0OyYRQzs9lh7jtNWFELr1EK4Zii3BVK+roFwm4DUUsoOLoT=wzclCy7sFqpzfjMPEXuN=nt5=XfBGgNTikEMu/wY6OfZO38dd\\\\\\\"}]}\\\",\\\"scene\\\":\\\"DO_NOTHING\\\",\\\"ts\\\":1758348827922,\\\"encryptedExt\\\":{\\\"wbType\\\":2,\\\"cipher\\\":\\\"C0LGigOye5jKNxL77s8cnfl08psuBgiYRDKVoomJTNjsRwoF5CwpoCuKLIl20843mB/xQTI+GXzLMWr8Z91GUQ6VVbR+2xoQiLy3fiOWgnWhNj1J9MXEn4zyTLTo9sa3qOk=kjFVomUFBSF42kYQ4KJKJAO=EQ4ojiNtrbLlUG3VDBrRLFnAWZU/uRUuug4nEIbo/W93QDjZhN3/OMN7klj9vM=1Qq6TbRc98F0YJSVKkLOOs83xPr4WWEQFlCDeh8Lfb7DHcxNKwWrrcASjXidd\\\"},\\\"payload\\\":{\\\"wbType\\\":2,\\\"cipher\\\":\\\"=6c/x80nbSY6FwLsMoDYSXzBp857L8+yxErqjXWxRWqq+jC2zK1D6J=Cn+at5H3Z3zzMVq1xtM1WV8RcQL=u8JDTTxcFwQXm3wLHUAkLG4=3vTMpb6jRWlyHvM57VKtekSqQ9nw0pjqSAGqXYHp3k0q1oET7SLi9G5aT9ImNS9E1kTX/5PnqJrKrW60Nxqa8HwY0hf6A74z4bNMjStuL8kCoblJKMPPiwr2c4O53KolRuX6NiWKxgfiStMxK0m1YIahpoOLWPk8UAmKYXuB0eEXF4Q7NXiGcj+48y22e13oXM8KX3OfO/t5b6BL+2mPulSl7tzqYvkJc2gWQiiQjImX4isVYbvcZ3iWU/UUKozmXTlwEPI9lKx5YpSQk2sxhscj=IktMAZiFkSStZ9qpBVCWZ28fFAxpK4E7ZqDDsxii3UmVZYFt4vzQ2gA+ZMmM5h=1eLaxVVmgZwqyZgPOAr6RGOmL=nqry8/G26KYDZosuI6gb0JRwTTk/8HPp7uRMyfg0yDo6o/qfK2X=lEv6A/7cmsci7sEI6Afo+B/9h+hiuAs92KW9p7AEAvEHh7qcpxLx4=7J6FPv543n/CzJwS7Pv0iXOsHfyg/TXQaiez5P167WBLETL5orqia1etNV7X2XfRt14tS32WWzD8xJ4WN76UD21wzxrRSjcPfvHAUfcGtgtya/ohKutx+nXf/gF2lJqr/2EYl3xl7A9mGKVa1OVpnjKa8SeEkPWnYEGVV9RKqWMNf0mcwnwgyxCa8iYokym7n9GbxxyrLV2CLbGKhQL9eOy3zznfHXxqCLmBetUrACcIQ1gEm4LzUHYC7fD/7O8mKax27N=/bK3j5s1OkD1YoHsiGJhiaQ4a6yVmBT=Hph0iSoUJ+lTrGO44YTkpNIBq/LewTnyeIwBPph5ruUIRZEURRgy55xiUZrLnfwppi8CIDaYvj08lFG3W52IWvpNjUUZivb1vjW39gMSNiueTQJBPJ33nw1j5DeVliysjW0lNu=rhNi9plQSJE+kT3+Ylafkt6C/i/5ANit+a8xca0pJgtGNoKhA=4+ZAbcUmGA5NOOCNuJ/oJRKZCuWCB8Nis0gCiP96v5oBXVkQBim2DJo9RmcOQxTCHngAuTCgip/kGz3Y/MMtPJ8HvtQD2pz5EFGwuZ/j23nin6qm+zObpkMwhR+n/e=Qj7BceC0Kan96n4R0NgNUiFemvNKut4XZheJfB8bBwUe5xyiioCoP0sgU0SZJDu6gP2JxT=MT4QFpJkDsCjhb+XyL8J323I2WjWv05BQrnFXOrsf/+qTPP0gEnIT/4iMawDpz1T6+2=FTJs3Q8cRyHuF0WvnZhs875UKr2kHuPS5c3LyLLb40SbfJqB39p=/Auq3hyUIV8JO/oIWwAK+BVBOw1ysXNnb4aIxENv9EWgsJmrM1vvI14auXYRIT9M+4=1IRd\\\"},\\\"s\\\":\\\"AxtGzmgAAOmNoDB7+Pv7+8zOzJn44uPrmJqLj5iTmqXJy8rVyszOys/PwsLCwqXKpazInM2rjZOokMrKi724lo+8sKqsmo2LkCsz9Ooq5xyH199emyOD+RQ=\\\",\\\"buildId\\\":\\\"5d6f1bf0a0cbfad3b5ae24c5410ea8bb\\\",\\\"apiVersion\\\":\\\"2.0\\\",\\\"triggerEnv\\\":\\\"web\\\"}\",\"scene\":\"CONSULT\"}},\"operationParamIdentify\":\"independent_component_program2025060902501909\",\"source\":\"independent_component_luckdraw_02378770_industry_luckdraw_action\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //升级建筑
    fun lifeMsgProdBuildingUpgrade(buildingId: Int,energyCost: Int) :JSONObject{
        val method = "com.alipay.industrydoraemon.biz.bigcity.rpc.constructBuilding"
        val params = "[{\"buildingId\":${buildingId},\"energyCost\":${energyCost}}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //查询全部建筑
    fun lifeMsgProdBuildingList() :JSONObject{
        val method = "com.alipay.industrydoraemon.biz.bigcity.rpc.queryAllBuildingList"
        val params = "[{\"provinceCode\":\"110000\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //查询已经完成的建筑(5个为一组)
    fun lifeMsgProdBuildingListFinished(group:Int) :JSONObject{
        val method = "com.alipay.industrydoraemon.biz.bigcity.rpc.queryFinishBuildingList"
        val params = "[{\"groupId\":$group}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //选择建筑
    fun lifeMsgProdBuildingChoose(buildingId: Int) :JSONObject{
        val method = "com.alipay.industrydoraemon.biz.bigcity.rpc.chooseBuilding"
        val params = "[{\"buildingId\":$buildingId}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //上报行为--领取建筑奖励时上报
    fun lifeMsgProdBehaviorReport(buildingId: Int,groupId: Int) :JSONObject{
        val method = "com.alipay.industrydoraemon.biz.bigcity.rpc.reportAction"
        val params = "[{\"buildingId\":$buildingId,\"groupId\":$groupId,\"operationType\":\"buildingPrized\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //==============芝麻树==============
    private val playInfo:String ="SwbtxJSo8OOUrymAU%2FHnY2jyFRc%2BkCJ3";
    private val zmTreeRefer:String = "https://render.alipay.com/p/yuyan/180020010001288004/zmTree.html?caprMode=sync&chInfo=ch_zmzlzms__chsub_zlsy_icon";
    private val zmTreeChInfo:String = "ch_url-https://2021002135657012.hybrid.alipay-eco.com/index.html";
    //芝麻树获取任务列表
    fun sesameTaskList() :JSONObject{
        val method = "alipay.promoprod.play.trigger"
        val params = "[{\"extInfo\":{\"batchId\":\"\",\"chInfo\":\"$zmTreeChInfo\"},\"operation\":\"RENT_GREEN_TASK_LIST_QUERY\",\"playInfo\":\"$playInfo\",\"refer\":\"$zmTreeRefer\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //任务处理
    fun sesameTaskHandle(taskId: String,stagecode: String) :JSONObject{
        val method = "alipay.promoprod.play.trigger"
        val params = "[{\"extInfo\":{\"chInfo\":\"$zmTreeChInfo\"," +
                "\"stageCode\":\"$stagecode\",\"taskId\":\"$taskId\"}," +
                "\"operation\":\"RENT_GREEN_TASK_FINISH\",\"playInfo\":\"$playInfo\",\"refer\":\"$zmTreeRefer\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //首页？
    fun sesameHome() :JSONObject{
        val method = "alipay.promoprod.play.trigger"
        val params = "[{\"extInfo\":{},\"operation\":\"RENT_HIGH_SCORE_HOME_PAGE\",\"playInfo\":\"SwbtxJSo8ONDP%2F2NlFy0H2DwfakQ0s%2FB\",\"refer\":\"pages/home/index\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //芝麻树-森林能量查询
    fun sesameForestEnergy() :JSONObject{
        val method = "alipay.promoprod.play.trigger"
        val params = "[{\"operation\":\"ZHIMA_TREE_FOREST_ENERGY_QUERY\",\"playInfo\":\"$playInfo\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //查询芝麻树情况
    fun sesameTreeInfo() :JSONObject{
        val method = "alipay.promoprod.play.trigger"
        val params = "[{\"extInfo\":{},\"operation\":\"ZHIMA_TREE_HOME_PAGE\",\"playInfo\":\"$playInfo\",\"refer\":\"$zmTreeRefer\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //点击升级树
    fun sesameTreeUpgrade(trashCampId: String,trashCode:String) :JSONObject{
        val method = "alipay.promoprod.play.trigger"
        val params = "[{\"extInfo\":{\"clickNum\":\"1\",\"trashCampId\":\"$trashCampId\"," +
                "\"trashCode\":\"$trashCode\",\"treeCode\":\"ZHIMA_TREE\"}," +
                "\"operation\":\"ZHIMA_TREE_CLEAN_AND_PUSH\",\"playInfo\":\"$playInfo\",\"refer\":\"$zmTreeRefer\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //点击升级树，在没有垃圾的情况下
    fun sesameTreeClick() :JSONObject{
        val method = "alipay.promoprod.play.trigger"
        val params = "[{\"extInfo\":{\"clickNum\":\"1\",\"treeCode\":\"ZHIMA_TREE\"},\"operation\":\"ZHIMA_TREE_CLEAN_AND_PUSH\",\"playInfo\":\""+playInfo+"\",\"refer\":\"$zmTreeRefer\"}]";
        return JSONObject(RequestManager.requestString(method, params))
    }


    //============天天秒杀================
    //秒杀任务列表
    fun ugShoopingTaskList() :JSONObject{
        val method = "com.alipay.ugshopping.biz.rpc.promo.queryAllTaskInfo"
        val params = "[{\"needPollingSignTask\":false}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //完成任务并领取奖励
    fun ugShoopingTaskHandle(taskCode: String, subTaskCode: String): JSONObject {
        val method = "com.alipay.ugshopping.biz.rpc.promo.finishTaskToReward"

        // 根据 subTaskCode 是否为空来决定使用哪个参数格式
        val params = if (subTaskCode.isNotEmpty()) {
            "[{\"subTaskCode\":\"$subTaskCode\",\"taskCode\":\"$taskCode\"}]"
        } else {
            "[{\"taskCode\":\"$taskCode\"}]"
        }
        return JSONObject(RequestManager.requestString(method, params))
    }
    //签到？
    fun ugShoopingSignIn() :JSONObject{
        val method = "com.alipay.ugshopping.biz.rpc.promo.finishTaskToReward"
        val params = "[{\"taskCode\":\"POLLING_SIGN\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //获取实验结果
    fun getExperimentResult1() :JSONObject{
        val method = "com.alipay.xuexiao.MgwDeliveryFacade.getExperimentResult"
        val params = "[{\"unitCode\":\"COXY_TCEM\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    fun getExperimentResult2() :JSONObject{
        val method = "com.alipay.xuexiao.MgwDeliveryFacade.getExperimentResult"
        val params = "[{\"unitCode\":\"ZATQ_RVSB\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }


    //多懂一点小程序|签到
    @SuppressLint("SimpleDateFormat")
    fun rceduSignIn(userCode: String) :JSONObject{
        val method = "com.alipay.rceduservice.master.triggerActivity"
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val params = "[{\"activityCode\":\"20211115RAC000001\",\"params\":" +
                "{\"DLSignDate\":\"$currentDate\"},\"rewardFlag\":\"PRE_REWARDS\"," +
                "\"userCode\":\"$userCode\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

     //多懂一点小程序|查询用户信息
    fun rceduQueryUserInfo() :JSONObject{
        val method = "com.alipay.rceduservice.master.queryUser"
        val params = "[{\"outBizCode\":\"userEdu\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //每日任务|获取形象才能开启
    fun dailyTaskQuery() :JSONObject{
        val method = "com.alipay.rceduservice.garden.queryUserTaskInfo"
        val params = "[{\"outBizCode\":\"userEdu\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }
    //处理每日任务
    fun dailyTaskHandle(code: String, status: String) :JSONObject{
        val method = "com.alipay.rceduservice.garden.editTask"
        val params = "[{\"code\":\"$code\",\"outBizCode\":\"userEdu\"," +
                "\"status\":\"$status\",\"type\":\"DAILY\"}]"
        return JSONObject(RequestManager.requestString(method, params))
    }

    //=========================青村特权任务=====================
    /**
     * 青春特权--查询任务模型
     *
     * @param chInfo 渠道信息
     * @param skipTaskList 是否跳过任务列表
     * @return 响应结果
     * @throws JSONException JSON 解析异常
     */
    @Throws(JSONException::class)
    fun queryTaskModel(chInfo: String?, skipTaskList: Boolean): String {
        val jo = JSONObject()
        jo.put("chInfo", chInfo)
        jo.put("skipTaskList", skipTaskList)
        return requestString(
            "com.alipay.mobileopl.youthprivilege.rpc.mgw.queryTaskModel",
            JSONArray().put(jo).toString()
        )
    }

    /**
     * 青春特权--任务报名
     *
     * @param taskCode 任务编码
     * @param taskSource 任务来源
     * @param taskType 任务类型
     * @return 响应结果
     * @throws JSONException JSON 解析异常
     */
    @Throws(JSONException::class)
    fun taskSignUp(
        taskBizId: String?,
        taskCode: String?,
        taskSource: String?,
        taskType: String?
    ): String {
        val jo = JSONObject()
        if (!StringUtil.isEmpty(taskBizId)) {
            jo.put("taskBizId", taskBizId)
        }
        jo.put("taskCode", taskCode)
        jo.put("taskSource", taskSource)
        jo.put("taskType", taskType)
        return requestString(
            "com.alipay.mobileopl.youthprivilege.rpc.mgw.taskSignUp",
            JSONArray().put(jo).toString()
        )
    }

    /**
     * 提交青春特权任务
     * @param taskCode
     * @param taskSource
     * @param taskType
     * @return
     */
    fun taskComplete(
        taskBizId: String?,
        taskCode: String?,
        taskSource: String?,
        taskType: String?
    ): String {
        val params = JSONObject()
        try {
            if (!StringUtil.isEmpty(taskBizId)) {
                params.put("taskBizId", taskBizId)
            }
            params.put("taskCode", taskCode)
            params.put("taskSource", taskSource)
            params.put("taskType", taskType)
            return requestString(
                "com.alipay.mobileopl.youthprivilege.rpc.mgw.taskComplete",
                JSONArray().put(params).toString()
            )
        } catch (e: JSONException) {
            Log.printStackTrace("AntForestRpcCall", e)
            return ""
        }
    }

    /**
     * 青春特权--15s浏览
     * @return
     */
    fun triggerPointPrize(): String {
        val param = "[{\"bizId\":\"DO_FEEDS_TASK\",\"sceneCode\":\"STUDENT_MONEY_CHECK_IN\"}]"
        val method = "alipay.membertangram.biz.rpc.student.triggerPointPrize"
        return requestString(method, param)
    }


}