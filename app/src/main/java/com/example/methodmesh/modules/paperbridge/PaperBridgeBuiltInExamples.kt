package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Runtime copies of the canonical demo assets kept in this module's docs/ folder.
 *
 * Module documentation files are not guaranteed to be packaged as Android assets.
 * To keep the shipped example usable without a manual import step, the exact docs
 * PDF/XLSX bytes are embedded here in compressed form and materialised into the
 * app's private files directory on demand. The docs copies remain the human-facing
 * source artifacts in the module handoff.
 */
internal object PaperBridgeBuiltInExamples {
    const val DEMO_TEMPLATE_ID = "paperbridge_designer_demo"
    const val DEMO_TEMPLATE_VERSION = "1"
    const val PAPER_FILENAME = "example_paper_form_for_designer.pdf"
    const val XLSFORM_FILENAME = "example_odk_showcase_paper_form_transcribe.xlsx"
    const val PAPER_SHA256 = "bce47af494812361fe443c6d7fdd7bd7c265a06c35755cf221396d51fd6b9a3e"
    const val XLSFORM_SHA256 = "fa9d0dc8640de03e1d110e372b219bdee806c1d730ba316fea01ba6d08aa368e"

    fun isDemo(template: PaperTemplate): Boolean =
        template.templateId == DEMO_TEMPLATE_ID && template.version == DEMO_TEMPLATE_VERSION

    fun paperSource(context: Context): PaperDesignSource {
        val file = materialise(context, PAPER_FILENAME, PAPER_GZIP_BASE64)
        return PaperDesignSource(
            path = file.absolutePath,
            mimeType = "application/pdf",
            displayName = "Paper Bridge example questionnaire"
        )
    }

    fun odkSchema(): PaperOdkSchema = PaperXlsFormReader.readBytes(decodeGzip(XLSFORM_GZIP_BASE64))

    fun xlsFormFile(context: Context): File = materialise(context, XLSFORM_FILENAME, XLSFORM_GZIP_BASE64)

    private fun materialise(context: Context, filename: String, encoded: String): File {
        val dir = File(context.filesDir, "paperbridge/builtin").apply { mkdirs() }
        val file = File(dir, filename)
        val expected = decodeGzip(encoded)
        if (!file.exists() || file.length() != expected.size.toLong()) {
            val temporary = File(dir, ".$filename.tmp")
            temporary.outputStream().use { it.write(expected) }
            if (file.exists()) file.delete()
            require(temporary.renameTo(file)) { "Could not install Paper Bridge built-in example '$filename'." }
        }
        return file
    }

    private fun decodeGzip(encoded: String): ByteArray {
        val compressed = Base64.decode(encoded, Base64.DEFAULT)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
    }

    private val PAPER_GZIP_BASE64 =
            "H4sIALvioWoC/71a23LcuBF951fgxVX2lo0hbiSQ2tqUrrEqvkWaZCsV7wM1A424niFnOZRXyk/mIX+Q/EgaJNHAkCOt7VStVeuZPWo2+jRwGg3Qzz6cnr9i" +
            "VCbP/vPff/07YSQl9fXPyfffJ7MPTb28W9iGPN8+bJc3L5IffkhstXS/5pHZ/GFryexDsbK7ZHZS31UtYcnsz+VyR/5BJBhekp+iR8Xk0ZOiLdb1Kul9EOf7" +
            "MnpARg+c1FVrq3ZHVGc0e2uXZXFc38NIKfwooyjPVUa0ZFRro2Hk2aXd1XfNAjw7D+fgof/CSNY7OWevUpLjd0b08J0T42NxyVhc2RYGml1sIM7j4fNk+LyA" +
            "FJyek9nc3rc939ll3RatJSnwbIqqG97BIV/J7Kiq6tbl6SfHvgFqE/oqov/GVqv2luSaa2exaxtbbJJf4Id1/Pu/FxtCjufEMWSczG8Ik1SS+RtyNgc799Os" +
            "kuqRhDWW3HyXpN1vnVkiNM0zkWuiU005k5xsIowJyrTMiZA0F1JIQPLeihu0GZBFwsHKCM4jq3TsKSB+PHguYIJyKTKCnnJjRuMhskgwKsQiNoOnKb9FAhmo" +
            "EpVLqhjbIx4wH67KUyo1j4irTHkbJK6ylCrJIuJKZWNPiIRAImwIFz0hJRwvIo5RBavAxnua8BsTF4JmuVImJg7pymTGVCAu+WCFgSASEQ9WnlLwhIgIz3mM" +
            "w/SkKhWBOEz9aDxEIuLBykcePE34DcRxKUTEA+bDxUWFlHDpRcRxgQardOwpICEQxDBc9ISUcLyIOEaFGEaOnqb8OuIo9q5ocMKyrmjAwhmKBuC+vrh6IWVq" +
            "SJ6DM6nJfEOefyi2sEscN+VyZcnSbmryy53dtWVdVUXZ2Bdk/jOZfzd4cmVJU9UNkeIQlBvppoSK7g98as2FGqKajp5xmivOuuGP10X1iWy7ID7bZgfjkpsa" +
            "vpe7u2JNNsV2W1YrUlRLslsUVQVmrQuvWkWRjZNghgD1UzkQGc0UROmiuDg9eze/OL84OZpfvH835czSzuOTOeWwERujhqQ2bbkotwXsCRenkb8KXOWUSZOR" +
            "TMNEMulWEqfCCKjDjBrDNHN1/CoeRBhNtZDZ/iB/K3dlS6q7zbVt9oaQsGiU0TIMoQxV3VYxGeJL05UJRTOe9Wtm/v706O/flKUMSihLFe/cvC4+W/JQ3xEL" +
            "W21F2npZPPyRzMvFJ/L+3Rk9nDaVK6oVz9zGqDXTnTZ56mZynDYGUnF5VZpTpfO8G/Rd3d7ur57YOQiegeFX+JaaGjYs5iOyLtt2bR9xDqVapUx+jXcGiRNZ" +
            "P99X9eYRzxKKouZKf4VnqRmFqmyGuG/u1iA2W6y/aVYlFDWRiX5xXD1stm292e3N59GbN6S9LVoCel4/PDK1UDYoYzDSl9MQBkxlJrqRz+3nkRJECgtfCvE1" +
            "rgUUBiHSkW/ojVe3j4StQFSpMV8TtnLbSD+tp2XRNLe1LR6J/Mu9h8gj969tsSwWt5NC/mWyF7AA81T30nl7dnT118uzt1Arr8iMXH04O7mA//mmJcMN7PVD" +
            "HZjbDZT/or0DQh+fn3x8cTjPXBiamiyPostpKiHESSagI4JNycC2C42H0apfmSf7kWLaXFGVGnZWGIm7Prpbxlu7KDdQlq6LZlEv95WHj/A0oxyWCIMdFcpM" +
            "Dr04NIRDL371xJYpmclS2DKlhO3dEFcYTMZGW6aATX/oHsDtsGEf3dyU9z4qsi6u7bqT7wMpq10JENS3Hbmu7w9tkFQP42qhYNeGT6Vdx3X5J2CFeWWw5pRM" +
            "oWmGopXS1KUYsXXEy7EyNPu2PoAJWAKG9yXoHPb8boOH2vwH8snaLSmgJN3A6Y9crwsoIYu6cbv/pmg+7Vx7UF6vLSWnNYFTGPm1KeG0Vn927cGt3bOlh2c9" +
            "hAE9fKZUX8FP7a5cVXYJxYvYe9ssyp0l708uoem4b19Chlu7ss1LaJRgcRTrl2QH8a7tq8VtXS7A8u3lS7K5W7fldg/s+hc/ZYti65Y6PTQ/JEn+ksCREJJO" +
            "3H+HznjJj9Du7p8ZgdhwFO4OjclwaEziQ2P/xU10IvPpsTBgDEqlUlB5oFNNpYFp5tn4WOiRRQKtpuYctijEGBt7Ckg4rSCWQ3PDc0HQUQ6lYXwqTPFU6INC" +
            "DAP3jqbsok4Z+EMdJb+6LHBqJllADAqfguLS1RgFWwHwA82PsjAg0PlzKqGj5AFjcAba9xSQKAsBg0Lljj7eUQ4zn46yMCCQBR8UYhGZ3tGU3SK52rsbEFP6" +
            "AYNapaD8QKWjqXZfNONj+h4B+jkUMsliK4X0B08Bie4GAgZdnYEON3hK5Yg/IsAfo/JYxGbwNOXXLQPezX00h9KTj7DhgTVkLLLNDZ/Y5nrPVqoDyjJjrLeF" +
            "ujOxPWh6SMIKWvvJBQdiXniuWYZd1gRxhguOIGGlAMuhbwhWoKGRJ0SiCw6PeeWhIxRndL+RhvuNIahg5QNHRxN2hyWs3EF2cs3jMS88lXOq00ibURJQwQp6" +
            "Js5ZHlkpOJftO0IkvuVBrBceOkJtRklABfuYglGgMviZcBsJWGVmSh4xLzuV5bDZMxmkGd1xoYBBhZSZ7nIPrZhnj57YlH3ABtkFT16aYbwg4BBVsEI26MlM" +
            "EoACjqZQBvKI7Qktyj+f2I4ErNy5aKIrNcIGW1D2xFYesn1SwfFNncekO7hnOQ8KhvZrdHOGSKTgYOW1GTx5JL6p8xgX7gaqu8gcPMGaH42HSCThYOUjD54m" +
            "/H5Dw3s3lgMmQQsZjIYalhnVowtLj0QaDlZencGRR/YuLAcM6p/QqlP64AkOW3J0YemRIOJg5OMOjibsHlNxTB8xRY1RzAQVw84/4o9IpOLIatBn5IlN+SMG" +
            "nwCZoGLY6Mb8TeDvo4qsfOTBk5kkYKpiUFIg79e3f2CkYpZPbdnI1qt4T1tqhI1UHNvKQ7ZPttLR04ih9rBrRX1iAxGpGDtgxLBJRk+IROlEDLWHnlCfOF6k" +
            "YowKMYwcPU35/UYzHefBYyg+7FtRoOH6PcgYe2DEsE1GT4jEefAYqg89oULD9XuQMUaFGEaOnqb8Hmun4/cPiHn1YeOKCsWAIh1jExysfKOMngISvX9AzKsP" +
            "PaFCQwKCjjEqxELk3tOU36F2OtJxWOH7egsrMZ/ajnSM7XS8qswYG7XTke1BUzhg/1+HbPbEIbtKDGgUujdh4EF3MdWphXIFI3ENfwnZ34fg62rw1F+hJN0F" +
            "RRIuKMCRu/JJ9i5HPqZQBfbvRxq7KndtU7iXNqQtmpVt8YbkYwq8Dl+SDJbOwl1FJPPvXAQuO7ZaDq+nh/fYWfQe+7jY2e5F/Oy1XX+2bbkoktlZtaiX7kXN" +
            "7MeyOqp2pQeS2btiY91tUTK7urtuu/fn7i068y/Tna/olXn++w2lf7+hzNNDvTqu18svGY9/4Xj3jb2B6sTSJMU/JFPu1viGIAYLtP9NFTAlJxhjYopBUR1j" +
            "EvqlEaa5mWIynYyhYbedYBnULcRgfZdr23QZvCr/aR232WUNy1r0/+LjorqpO61e9v+8omjaLgs6F2ny7NnZ+/Pkfx0Wr7EwIwAA"

    private val XLSFORM_GZIP_BASE64 =
            "H4sIAGjZoWoC/41ZBVhUW7QeGqVTBKQkZejOoVNaumEGhhpqaEdSBKQEJKREOiSlS0CG7pZUQlIQkI6H9773fPLufd+b8519vj1n" +
            "r3/2rPWvtddeW1MVDZ0UAABgA8D5bKbpHHfEDm56IFQAgODm6e3I5eXs5mDl7OzA6e3kWD2qBgvmJsRYKjao4kKG0N57Do5Xq20O" +
            "5Fm2wJjtttb2PS+yk39X2tADOyTXyDy08TeZ/1RK0YO5GNjtZrHuQVEC8ipnPGD44j1dtqMYliQZgmM8v1JoKkah88Z4UDlmnRRi" +
            "qdDa0BOy/uQd8BNfvsg7SqjcY9hX+YypObatL427iaOePGXUnlKqTV9Zyo2PBOF0AZ2T+A+a+kxeR8UGa1ZyQrTayhivFIkjKyg1" +
            "pLnwEWdZcmlPKtvGoLaoyGujQ4oSBd+PpFQK32QnKOyKWoggMWcbFO0JJSCQvWeqQpOgiCBq9fRELv/+Hcpn5pGLmV/2MDT/0E+Z" +
            "o7r/CQoA8BIfAMD7Wz/ucB9HiPsv7azqaznP0hFesYcddzLbTbIWGefqxh6NJh1Zln1C4o+ikMf2z5RlArqORCEORUxOT1pPvvcj" +
            "nqaB9k7XSl8F1MwZZOmG9rB2hhWb3JPRvb9Qt9+Huzi/wO++5fkxczJNbFxJ03MB7L1jIqFBnFpWD/bMcSilvJMrGCciNpqn4ugk" +
            "zF6iER8bOXbowJCiOf0w0UjkpVuzFmfHMicp83l4DS7vY2yFcAJWZh9cCW609g1CjbPISwRZ1zVKrMMwZor9a8vPZmXBMDwqZiLA" +
            "g2U7MBBw0Wwz6JpdAJB6f9lU7BP6Wq9J1hHrmq+Q11EROZZ9UIKpJWc20eHSGaK+MWEVI9R9rXHSdz8dmJTfkT8Fl7b173KO2svT" +
            "+0w616Spj4usXZhVFhJdD0ET9yn052p0JK5MJS6MyhlFsPOmWa3oVghhSDkoFEyGr8t2X5LKHm7VtgtBQEM/AGDAx01Kzxiq+q+h" +
            "ofNUNC9YJ+zX7wQsNBJYtPlzPP+RJZCWjHoKJA1qBWix9fj9qEQALrlLnq2phUSgOvXGaPj1RZHUg3LdS6mFTk6eU2t/uS/0a/hg" +
            "noxb2yNx7wz/j2eZ39jdVD7H95UcrfAWIzxSJiS/JD7ViU9s0VltWJvQL92xxNpxgNqaH3aZUp+CinBBvtF7fkmi26FOLueLvbPm" +
            "f1LkzATFEA0NAPh8ww+SvykCh0KcIH+3PL+I0qw/65bKTXpBprCLJ+R+EAIm7oLRjtrl9xB000lFEdIoR6e37tLBUCOX+T23sVSt" +
            "hmbkv5W3T9Wep0HLwlxNhgmT9TGHCaMiKMb40Y1fPT1o2xkQSHuJu6qUza1IbwDWfHp9ifsBoc6a2UEIlnFcaxjMxl2NNSejkjsW" +
            "Ub6bKNtWdjdxkydY7PFY2nqiZZ0vPg+G0wKTtMi7oCQcIkuNEZTKfaMLq0TQRRQX9pgZ/Z02/LG6aYnsZgBloAkQGyeQHiQ15lz6" +
            "UzAiLNyCyYUJpGbxgKjVgDc2d0tw1MDoqn+bgjEojMdlI2mRM1vuI6vMh8TsILUugafFKOwF9t50fkspR3RSluVduIF+zgNnKMS6" +
            "eLbLVKYYfVj5tHRaE8P3MebuTyIsjtEYiygbsgNm9gerEi8B4b3YeVzWdVunLbMfjR9rx9Ji/QjcolyQDd61aBi110V95Bt1zK7s" +
            "EgJeXN1zbtPDIKvs1bQoCq/FU7F4IsjK6D1QfhQ29liJRlhx74HUpxjUQxTb6I1G1GhNWnqnDVFM03nceoH65KeMJI+7470frr9S" +
            "dW2iEzazlFmRU6jOnQbjtFO203BDyqkORHV7q9XaZC0CekxGpbnJJt5sCWhIpMU7XVWFbeUJGYsFuOPLe7W5kGZC768AV4tCib0q" +
            "8YcyhkdG8DymWTZjvdwqCRcvymOXCghN2QjangSvsn3pt29cJWJk5cTRY1l//RPIhG+j4wVGeDposPIMWUS+aUImxBXabDXs4crr" +
            "5XhfWTJJc9PnzBWpkhrSzqJkXabUsJ82r32/6gMKRr7E6q66y8JV/Y1dF4racYpKWTyX0jey3iEhpxzEwFMxqkZbX1BF3XRhCBdh" +
            "q0GtyAcu2DlUQvwtqFG7+B6gQXnW3rEFkFtPVg+dXL4zfgGOb/vc40fg9DxpOVGHiH0VN/f4W/VXjwbRPtefz8Y/VudtT6Y+I+Zy" +
            "kcTsrc85UVzyH9dhLxY/rijc1FxVo3wvQT/UEjviaXrqESrqODNqnTrFI0J1EWivB7tC+dNn8Gjq1yE3Pfebm/Q/wyrU0g0C1oG7" +
            "2cFs/4quGG1adzvocAHDxUpXtnOSyNjPGfKfP9xlJo/Hi2k9TnRycwdRGjUyep8BosG7iq/Z26YGu7BqPFK0JuZWmCltg6w8FeXm" +
            "cVz1Fp3jena2g6sKd5dlmrFWZXFeuXboYzuvmGBQl/r/OS32CmaZsjsAwCAQAKD4vRq6QyEQuDvXX4+//DnXyMF5WZD8kh1di19H" +
            "ytWxZ3eSLzk8/oRnjPxgmsMzlowivGZaJUdF2wcee91CxzQbp+ZmtctiLCquqC+etJKbY74gbsNW12VCqMBSPmYvG2D3yUpmisSg" +
            "e+KUsMXnsm/5JPBHq1xQBXqMoUrniSsqCdvBj87nYcRp9CNWInkxhg1W4rLaHyjMzcPSrEkdjBLNTnY8rxlkQ2SYCobMtpCt7oTL" +
            "ZI+0T19J9HMbsrihiklgcz7pCMt9d4ZxX0taJjZL8fJcyirl8dvF53T0rpEb6DX0YXfpDY92ibe2wSoN5iOXY+vS8gSeh1+IDmHy" +
            "u7CNNh79Xd3DhcQFkbXNy4iFxZFZxWEsMoQLJcJg5EW+x3zBuxj17GLNvia7TNZytSbTtZ8t7C4VfRbp+zqMr0SXtlFcJpTMZS8R" +
            "s4FSUIdV9Ls9Am7XRpKrBoVzSUaza7v9A2y9kQNcAwM68Bc6OueF9QiZAn6BVl4hRZ9oIkV+XiTcidn1wktaOFotgYoKozMyT4O5" +
            "t/9xOAVZ3MO+y/5Wxbt8QHQjql0HS2b9+LTwCn6+7wYX/DQkj36YJdwTDbT3WZna754yejSQmtKWI/IKuOOeYP2sS7kJSZ8Vx2dt" +
            "ZNjdys7rnT9Dh0x5JV4dC2KRCwAt+Gv3oXXHRhIDAx5z17FD075SzOWGXWHAA5aONwSk+DGJ3gFygwLFcJUS+ThdcbFf4/JbpVhJ" +
            "T5IeJPK8JEAtWgOkv+fcQ2IMmizNYXCaLI1i0+QoAAKNNBH+fCCnpGmjQXQbFsZn/GtbYqp+Tsqsron5Sl/3NNoKeBxQu5zUTJde" +
            "r7avMj6NLvD+OCc8VffEGVikYUBhS5o0eQP3qLzVK8u5HJi9RGFjwAwcLFcyahAyPn6w3uaUFEcRbYmAInVlXtgnSBPodVJTyw48" +
            "5O10wT1GWiuPoJH4cis64HYtvjUbb78giha79JwNDlcnRbQfn7dVYRFFESOf2wO5v2oRlwpH5LSYNa56SbCSRMu3jwo/xvEApZvN" +
            "Bt/PYmSv1JSLtJ53O9luI2uzAy6dbAkV6HEgzE3iwlbCVDyyacb74vH9cQJOn8xP6ZRiA7VnjlizsEQeulxUz2/iNnsWjx18fp9R" +
            "doX3opZmDsjmAvQJnPhBCWrnuTg7ugCtX2QenbKKt15O/rj4zndIpwuxUwlpCFphDKaRew6mj2BIUxU2y3qyZOtMdSRBJBf6ofs7" +
            "dxnGJZcHpaPGlIwe35lR5pycxJ6QFsGOlm1Iu2tX8H1t8tMurIhXn/gTgAoa1cK26zTBLHoAPDS81kKMtB+ahA3qPOK4dg61btV8" +
            "4zyhqN/CstX0PpzTgOtFHzW3KNplwdmy68g7yx4zh5Qepx8qFoEuajbfvB1r1fWq0lIKfzLTPOaNU5NPySCtSbIhII/et0mc2dvB" +
            "jNDMjnlzqPsqLwp7/E6sZcL3YriY2GvYcCui5Sedis5RwLJmceH3maPeNcmivUMIzEEvIh+YBCT5OVyskxushlbF8JpxsUoz/wUW" +
            "eyPFC2vspevVjbuLQqEfPTzm1sMyBg8qH/q2FYykE7FPKqstVYouYK/zmN49bGAOTKYbJAnVUgMbiU1F5gjNyfMeUsncUeCJiPOF" +
            "9Xvmv/kBTXlEw2QN9xwTSjUuUWxoNpi3EvhmHQ1uc6WYaD9pEiMwM70aTzOyon61zX16Jvo8uHQKhieKmJ0jhhvv2PtuYG7uVpBE" +
            "dk+s8uDtEsYYzFEYkKF7apIdff9Uo6+bdpmRge/R3Wz2cGW/snYIqS3qraWb4znvhupnqq87zpF7cVlJXXYHbyqNrL5CuHJYLgRa" +
            "kASzVgNbIrSfOxluXplaZCGp5UOoVE8L5ZclHkET+HEsdfncgTjqHnYc3Q+7xz05MFcbHDljOe4NJxwkYFtXeyM+Fk2ZcbqsoX7j" +
            "dPmCmpQ4G2Yw2R7D2qczVzRhqe9vYvrs9CnZs7oz8XOnsyI5GWKNwd67ahIH9srRBWHC9R22NFk+9LKyPOKnX1K/bLvLE5YsEeHz" +
            "kS/3GIbw9o8oFAtuyiT+6EmhrFbg/hrPJyEpCLXbV0gDQ6NSD4tQul4xsgdZ66RSS+Tiduo6TpkvtIIOHpn52L9m8DmK94v/XIz3" +
            "3mxD6jiXCl/13KYTSq1PblOVwx+dh2aDeGj16vJK4ZPMh20BWLSDZBX/FMZucSLW4JVDgY//tDuM7yPQyhwdM929FUcbyLTWXhxh" +
            "8iUwCJm8xUvLjR+XL942LhwPNElv5A/COh0vW9VZpLbXtaPS6ZUp1D9AhlTuba628KSflcI3M9+oXy6PbtSt8akekJ+4cvhnBESb" +
            "KRgtdT2hPUUCK6EmWuEiUkstk84WnYaYolK8wz8GfJY3OyHX8j58BasDH6nTPrJFg7tZMavVdAwmmpDwStaJdaRj5XBkKq+1EZ6J" +
            "nYCwwHvMrtwJ1wHb0uS/rRB8bwZUMgOCf26dEQgcgPWlAATfRzjAF9vtYKuRNQSmNQQa1fO5vnzig+C/wprJktsJQ7yiQLziWl3x" +
            "+7gRVZzcCiY+Snv67zszkdHRJtHR6h5w+JyUZOmp44oZbRDMOnAdE4nxFQORSCZTxHLuZSBp8ViofR5g8ibpjr4tpyKXSpZ645Vb" +
            "qRN+5ecmyvhu5MwiExVJj8LCoiKVzkasyRBT6uFxfE+hn/VYNVOMjtN3uXvqcDsFggewpmc71PVMFUS9DzJilcjXlBcVHLeHdi42" +
            "6Fu/wL6BvUwbhARNixs5q+tJTIVHcu9LwGuvtJ/s/IyiQq+eEpjdEtt575Q/wGy8nsSqz2eEq4Lx1qwl2fQonfOcpXF7tLliw8jU" +
            "s3Agfb09nHNgPatPen51CZqEltTnNyTXr2BMKgmrw3+Jlt/Z1kPeCHd2bNB7aWrvztLg8k6VzxfqWgtzPe7WnxBs/45iXrBQOm2u" +
            "N4suCQ0kMGBcVc3szF98JjD7lG64fIbhGjX174kO/J7oh/qrp5qIa9sImrHpHyvzyM8SvgQ8AiBm8ZzJVmWXd/rqh5/DF4RHUkjS" +
            "xUaek5i+GZHV2SSYGffCUUQYgR/yS1pYjPNWGHVDUj74LJQKrdBZbWY1i0QUqq1nPwuIstZsyUGjLh7SQdFylgsDtKolSUl8xgq5" +
            "W9hieW6Y3lAHLvQVBkyjkUUoW10ajaOxy2ipXI0o+nq5hReusGK8q+sL0zSHJJsXUSOnlGjv+S7L2arD/9MmQhvuFcfV2UYoILoW" +
            "5icBpGUr6DXGaNjep71q10nAqWtfwjkeNsMaEe1dBR59RRR0duTK0dumpaOxlalrtD+TPMzlS/+jmy19Cda/Jnm8v5K89NcjsJtN" +
            "m5R/5/65jIxKnAKT7mTRZl+end+69Uo7RslMS3eL8vqiRiw/BLnBn1HeIbIGUH7qtQjtxLdIl4vfnwH33k/GdbfrTdgtPKn8epza" +
            "aIOQO5/VBXyifB9pLMRWFrbpQVpfgsoxh+7AHaWHYcTRrTac+KqYI6xp7EPE1w04kR09nvO9bUnE5hJt4qpmWPO7TO+nmIZ6CfuG" +
            "7YbjQfcfeUnycswqDX6ANixrrT3JFVcXLgZnBCufJlQ57mL/7NuY27ZOiafJ/OmdJgnaOrgezo5pXIeda8VCWchj+ms0vZ571qW7" +
            "rmlnuCiweYEb+V5ExUV7KkChSaJp5E3C5h2DAhdfepk+sRe0nhTgbHsgmwenx6QJkx9Yci54GsTLLQe9b7EdDmFH7IlpM6md+nxp" +
            "0hemNVz3UyEudMmOgRkCaOQWDhmcFRTOMjrYuWax/E+X6Lg2VpkeHjwtm2Yn6z9o43DQypO+WG6sW9flvHjxI89BZkdS7TAXFd8i" +
            "OErylJ8Oy6PPbN7tcToXpI6r2CJdQxK3W3q+SL+WUuebaj3ppB42g8H6WIy0r6bPbHHTA50NVbIwE7DuidfXjiH6k2ae/asuB612" +
            "IUtVO2odTc3e/Hgm26MP8R7W7Q1UTGnNDESbx4bGGDXpAyLshBu8Ik7USVFeB1h/ckUb7aNo0Q1XHqH/K1f4/uJKvJbDLDdhV2uv" +
            "K26lNBWLetVrzXwghzVXcHmSBGWn6KxVIjhH1Yri8rtsZaonkqpEoyH++3Ht1bexeUQ+lfCrg943uEkHGKgp7SqQItwYE7PFr5dH" +
            "pbNp7883HBhC1YWQzg24Dmue8YwtBco4oFqOFHWhXA+j8kxjfaYWD0bwJ3h8zsvOLjkcCDc2cp6rwntXSpstGs+/9ltZTtEbPpNS" +
            "ojhNg2kkATuUjDP8rfy9qao4E9vkBYZxFRXe+5yEEdm2xf6Htn33Umg0sMQRVC2UexOze6koracE4j52Mtufsy2IiG2HlmS0LmSw" +
            "QVxET0Md0AeHq16y5DudsIiww1ih6b6dOB/OsXJWSH9wq+Fa88uLq5P0epPnvZ3Dvvw2Hwfdfd13+LoPzlh1j0NkWoSJHVXZTDe0" +
            "/bGd2IZXZC0KSE7UP++gdYTA2ldkw8tE10AH+xlodrfUVn/vuz47dbFMdbtL6coYWf/ZZfx+hLv7OJabBi2zcVwk8i0ZGYKkVBsp" +
            "K8tkW5fsL+NyKRbBMyuo4BASDKk5EylWVnKjN6ZPmNxn2vj2fX+7tvW/96GAv+zL1PepmfXGvr9unJvvzN0gju5cnL/a3cZWcdCN" +
            "aek8IW7uds4wCQYeTm4GOgjM2hl8s0WVYPCA23AIM4AkxbUhjpbwmxHuUDsXd7obEZi7BAMUDncR5eJyt4ZCnCzdOZ1dILCbNzbO" +
            "bk6W8Juumy2Xi6W1g6UthIuXm1uQy+1/YjD8iUn3xMcF8v9BdLaxsbOGyDlbezhBYPB/AL41goHuiaWbLQQuwcB1q+7LQKcMlmDQ" +
            "toRY2whZi4CFbQTA/ELCAgx0XJLiXH/8X8k/HUZ67zKD70aZ3DcOQ/m3w/yt0/+J/pd+B8ZU1Tu4CTGXiuUWSEBfpzEFdkLxZkdx" +
            "hI+Ewym7tLZlfAXJqcPZ6Whk7li15WkLVZ0onv30YWxtboJ5Vu1WsSRb3Qu9O5L2FheTxFGIn0yUySt7GNY4I1umcy902tO632aR" +
            "oELGSWX5AXLkw5JHfwd2x7bfPeYynf6O8SWt1GwYgE8GPSPxAXQ+5cHgw4t8HuGp+3UEayuWSv6ky8mDEnIJDw7HpYoIM853gZmI" +
            "mEos46xmi6A7DHWan9y0eRO+vAzUxqGZzBhadxh9rsJnlZMQOo7cMzpQLbIqZimPsdsMXTuuTizGiJlAkk/2tvMMkuUnrUi82KAw" +
            "DviQd/mJlgCg1LfF9q4+p1f7Pbp+SRMqjfEebkUuvPd1dU4TpjtGk1Vbdm1cQI4FlL7ObTP+hf71s3P8P1WNSMV9SXujagj633VH" +
            "Y1lnGPzGqua/6OJu+kvRAwltKmg8hKHmkB5dwZn3dAyNO9LNRW+5CCurGrN6GMF5HueONE8ZzaXfqBARqVRgcKX0rVy3XPPSOu8O" +
            "WO2XGsgyUJWTVZfIoUBlbF2hnRHn+0bAtYE+ZlSyeC+l9xQpy1HaYIW9N+CwgHgvhJIZX08sJlm14SbKeElRacKd7gCTfUHZ/lSx" +
            "9Z+9rq4gniV8MR+WiNn5BfKJuw+yqxSq8Aompy8DErJ23wdyJr/GAivzBH6HolvsgjNGHV7eEzG47H28tC68rs+e8nH1AaVTjsRX" +
            "Vzy2GfwIlvQTaT/o8zdpDAvCdD5fQ0UOCd8o7pX7bYFwVpKDIYrpb/3WwK7CPt92Z17ImckarcXk7BdoAKuGQHrgI3M5ziHOd/sj" +
            "yf6omqooqKRo/34Y8l+fnEDA/z4auS18+6TgtzAdyq1zg9uityvIv0Vl0f6xnnwb4HY57TeAF+Y/F9duI9yufP1GwMf69zrYbZTb" +
            "qdVvlAPCf0+0bqPcXnR/o9CS/PsS/Bvln0P7b5Rr0j8C/e2fvx3Cfgtqkv9fAe02zm3//I3TeO8fvVVTFQPz14C7N1fMDQVx7v/q" +
            "/Qe0RaNEsBsAAA=="
}
