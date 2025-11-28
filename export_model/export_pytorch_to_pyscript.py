import torch
import pretrainedmodels

# Создаём модель точно как в train.py
model = pretrainedmodels.__dict__["se_resnext50_32x4d"](pretrained='imagenet')
dim_feats = model.last_linear.in_features
model.last_linear = torch.nn.Linear(dim_feats, 101)  # 101 возрастной класс
model.avg_pool = torch.nn.AdaptiveAvgPool2d(1)

# Загружаем checkpoint
checkpoint = torch.load("epoch044_0.02343_3.9984.pth", map_location="cpu")
state_dict = checkpoint["state_dict"]

# Убираем префикс, если нужно
if list(state_dict.keys())[0].startswith("module."):
    state_dict = {k.replace("module.", ""): v for k, v in state_dict.items()}

model.load_state_dict(state_dict)
model.eval()

# Экспорт
example = torch.rand(1, 3, 224, 224)
traced = torch.jit.trace(model, example)
traced.save("age_model.pt")
print("✅ Экспорт завершён!")