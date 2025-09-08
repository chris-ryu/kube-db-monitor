import React, { useState } from "react";
import { User } from "@/api/entities";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Key, Shield, CheckCircle, AlertTriangle, Eye, EyeOff } from "lucide-react";

export default function PasswordChangeForm({ user }) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPasswords, setShowPasswords] = useState({
    current: false,
    new: false,
    confirm: false
  });
  const [isUpdating, setIsUpdating] = useState(false);
  const [updateSuccess, setUpdateSuccess] = useState(false);
  const [error, setError] = useState("");

  const validatePassword = (password) => {
    const minLength = password.length >= 8;
    const hasUpper = /[A-Z]/.test(password);
    const hasLower = /[a-z]/.test(password);
    const hasNumber = /\d/.test(password);
    const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(password);

    return {
      minLength,
      hasUpper,
      hasLower,
      hasNumber,
      hasSpecial,
      isValid: minLength && hasUpper && hasLower && hasNumber && hasSpecial
    };
  };

  const passwordValidation = validatePassword(newPassword);

  const handlePasswordChange = async () => {
    setError("");
    
    if (!currentPassword || !newPassword || !confirmPassword) {
      setError("모든 필드를 입력해주세요.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("새 비밀번호가 일치하지 않습니다.");
      return;
    }

    if (!passwordValidation.isValid) {
      setError("비밀번호가 보안 요구사항을 충족하지 않습니다.");
      return;
    }

    setIsUpdating(true);
    
    try {
      // 실제 구현에서는 현재 비밀번호 확인 후 새 비밀번호로 변경
      // 여기서는 시뮬레이션
      await new Promise(resolve => setTimeout(resolve, 1500));
      
      await User.updateMyUserData({
        password_hash: `hashed_${newPassword}`, // 실제로는 해시화 필요
        last_login: new Date().toISOString()
      });

      setUpdateSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      
      setTimeout(() => setUpdateSuccess(false), 5000);
    } catch (error) {
      setError("비밀번호 변경 중 오류가 발생했습니다.");
    }
    
    setIsUpdating(false);
  };

  const togglePasswordVisibility = (field) => {
    setShowPasswords(prev => ({
      ...prev,
      [field]: !prev[field]
    }));
  };

  return (
    <div className="glass-morphism rounded-2xl p-6 bg-gradient-to-br from-orange-500/10 to-red-500/10 border border-orange-500/20">
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 rounded-lg bg-gradient-to-r from-orange-500/20 to-red-500/20">
          <Key className="w-5 h-5 text-orange-400" />
        </div>
        <h2 className="text-2xl font-bold text-white">비밀번호 변경</h2>
        {updateSuccess && (
          <Badge className="bg-green-500/20 text-green-400 border-green-500/30">
            <CheckCircle className="w-3 h-3 mr-1" />
            변경 완료
          </Badge>
        )}
      </div>

      {error && (
        <div className="glass-morphism rounded-lg p-4 bg-red-500/10 border border-red-500/20 mb-6">
          <div className="flex items-center gap-2 text-red-400">
            <AlertTriangle className="w-4 h-4" />
            <span className="text-sm font-medium">{error}</span>
          </div>
        </div>
      )}

      <div className="space-y-6">
        <div>
          <label className="text-white/80 text-sm font-medium mb-2 block">
            현재 비밀번호
          </label>
          <div className="relative">
            <Input
              type={showPasswords.current ? "text" : "password"}
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="glass-morphism border-white/20 text-white pr-10"
              placeholder="현재 비밀번호를 입력하세요"
            />
            <button
              type="button"
              onClick={() => togglePasswordVisibility('current')}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-white/60 hover:text-white"
            >
              {showPasswords.current ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        <div>
          <label className="text-white/80 text-sm font-medium mb-2 block">
            새 비밀번호
          </label>
          <div className="relative">
            <Input
              type={showPasswords.new ? "text" : "password"}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="glass-morphism border-white/20 text-white pr-10"
              placeholder="새 비밀번호를 입력하세요"
            />
            <button
              type="button"
              onClick={() => togglePasswordVisibility('new')}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-white/60 hover:text-white"
            >
              {showPasswords.new ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>

          {newPassword && (
            <div className="mt-3 space-y-2">
              <p className="text-white/60 text-xs">비밀번호 보안 요구사항:</p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
                <div className={`flex items-center gap-1 ${passwordValidation.minLength ? 'text-green-400' : 'text-white/40'}`}>
                  <div className={`w-2 h-2 rounded-full ${passwordValidation.minLength ? 'bg-green-400' : 'bg-white/20'}`}></div>
                  최소 8자 이상
                </div>
                <div className={`flex items-center gap-1 ${passwordValidation.hasUpper ? 'text-green-400' : 'text-white/40'}`}>
                  <div className={`w-2 h-2 rounded-full ${passwordValidation.hasUpper ? 'bg-green-400' : 'bg-white/20'}`}></div>
                  대문자 포함
                </div>
                <div className={`flex items-center gap-1 ${passwordValidation.hasLower ? 'text-green-400' : 'text-white/40'}`}>
                  <div className={`w-2 h-2 rounded-full ${passwordValidation.hasLower ? 'bg-green-400' : 'bg-white/20'}`}></div>
                  소문자 포함
                </div>
                <div className={`flex items-center gap-1 ${passwordValidation.hasNumber ? 'text-green-400' : 'text-white/40'}`}>
                  <div className={`w-2 h-2 rounded-full ${passwordValidation.hasNumber ? 'bg-green-400' : 'bg-white/20'}`}></div>
                  숫자 포함
                </div>
                <div className={`flex items-center gap-1 ${passwordValidation.hasSpecial ? 'text-green-400' : 'text-white/40'}`}>
                  <div className={`w-2 h-2 rounded-full ${passwordValidation.hasSpecial ? 'bg-green-400' : 'bg-white/20'}`}></div>
                  특수문자 포함
                </div>
              </div>
            </div>
          )}
        </div>

        <div>
          <label className="text-white/80 text-sm font-medium mb-2 block">
            새 비밀번호 확인
          </label>
          <div className="relative">
            <Input
              type={showPasswords.confirm ? "text" : "password"}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="glass-morphism border-white/20 text-white pr-10"
              placeholder="새 비밀번호를 다시 입력하세요"
            />
            <button
              type="button"
              onClick={() => togglePasswordVisibility('confirm')}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-white/60 hover:text-white"
            >
              {showPasswords.confirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
          {confirmPassword && newPassword !== confirmPassword && (
            <p className="text-red-400 text-xs mt-1">비밀번호가 일치하지 않습니다.</p>
          )}
        </div>

        <div className="pt-4 border-t border-white/10">
          <div className="flex items-center gap-2 mb-4">
            <Shield className="w-4 h-4 text-yellow-400" />
            <span className="text-yellow-400 text-sm font-medium">보안 팁</span>
          </div>
          <ul className="text-white/60 text-sm space-y-1">
            <li>• 다른 사이트와 동일한 비밀번호를 사용하지 마세요</li>
            <li>• 개인정보(이름, 생년월일 등)를 포함하지 마세요</li>
            <li>• 정기적으로 비밀번호를 변경하세요</li>
          </ul>
        </div>

        <div className="flex justify-end">
          <Button
            onClick={handlePasswordChange}
            disabled={isUpdating || !passwordValidation.isValid || newPassword !== confirmPassword}
            className="bg-orange-500/20 text-orange-400 border border-orange-500/30 hover:bg-orange-500/30 disabled:opacity-50"
          >
            <Key className="w-4 h-4 mr-2" />
            {isUpdating ? "변경 중..." : "비밀번호 변경"}
          </Button>
        </div>
      </div>
    </div>
  );
}